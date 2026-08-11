package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.MainChapterCompletionResult;
import org.firstfolio.learning.service.MainChapterCompletionService;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAnswerGradingResult;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.reward.domain.QuizRewardResult;
import org.firstfolio.reward.service.QuizRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAnswerGradingServiceTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 3001L;
    private static final long QUESTION_ID = 1001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 1, 30);

    private QuizAttemptMapper quizAttemptMapper;
    private QuizRewardService quizRewardService;
    private MainChapterCompletionService mainChapterCompletionService;
    private QuizAnswerGradingService service;

    @BeforeEach
    void setUp() {
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        quizRewardService = mock(QuizRewardService.class);
        mainChapterCompletionService = mock(MainChapterCompletionService.class);
        service = new QuizAnswerGradingService(
                quizAttemptMapper,
                Clock.fixed(
                        Instant.parse("2026-08-11T01:30:00Z"),
                        ZoneOffset.UTC
                ),
                quizRewardService,
                mainChapterCompletionService
        );
    }

    @Test
    void gradesFirstAnswerFromSnapshotAndStoresNormalizedKey() {
        arrange(inProgressAttempt(3), unanswered());
        when(quizAttemptMapper.gradeAnswerIfUnanswered(any())).thenReturn(1);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID)).thenReturn(1);

        QuizAnswerGradingResult result = service.grade(
                USER_ID,
                ATTEMPT_ID,
                QUESTION_ID,
                " B "
        );

        assertEquals(QuizGenerationType.HUMAN, result.generationType());
        assertEquals("B", result.selectedKey());
        assertFalse(result.correct());
        assertEquals("C", result.correctKey());
        assertEquals("정기예금 해설", result.explanation());
        assertEquals(QuizAttemptStatus.IN_PROGRESS, result.attemptStatus());
        assertEquals(1, result.answeredCount());
        assertEquals(3, result.totalCount());
        assertFalse(result.allAnswered());

        ArgumentCaptor<QuizAnswer> captor = ArgumentCaptor.forClass(QuizAnswer.class);
        verify(quizAttemptMapper).gradeAnswerIfUnanswered(captor.capture());
        assertEquals("{\"key\":\"B\"}", captor.getValue().getUserAnswerJson());
        assertFalse(captor.getValue().getCorrect());
        assertEquals(NOW, captor.getValue().getAnsweredAt());
    }

    @Test
    void returnsExistingResultForSameAnswerEvenAfterAttemptWasGraded() {
        QuizAttempt gradedAttempt = inProgressAttempt(3);
        gradedAttempt.setStatus(QuizAttemptStatus.GRADED);
        gradedAttempt.setCorrectCount(2);
        gradedAttempt.setScore(67);
        gradedAttempt.setRewardPolicyId(91L);
        gradedAttempt.setPointTransactionId(7001L);
        gradedAttempt.setSubmittedAt(NOW);
        QuizAnswer answered = unanswered();
        answered.setUserAnswerJson("{\"key\":\"B\"}");
        answered.setCorrect(false);
        answered.setAnsweredAt(NOW.minusMinutes(1));
        arrange(gradedAttempt, answered);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID)).thenReturn(3);
        when(quizRewardService.restore(USER_ID, ATTEMPT_ID, 91L, 7001L))
                .thenReturn(new QuizRewardResult(91L, 200, 7001L));

        QuizAnswerGradingResult result = service.grade(
                USER_ID,
                ATTEMPT_ID,
                QUESTION_ID,
                "B"
        );

        assertEquals(QuizAttemptStatus.GRADED, result.attemptStatus());
        assertEquals(3, result.answeredCount());
        assertTrue(result.allAnswered());
        assertEquals(2, result.correctCount());
        assertEquals(67, result.score());
        assertEquals(200, result.reward().points());
        assertEquals("NEXT_SUB_CHAPTER", result.nextAction());
        verify(quizAttemptMapper, never()).gradeAnswerIfUnanswered(any());
    }

    @Test
    void rejectsChangingAnAlreadySubmittedAnswerBeforeAttemptStatusCheck() {
        QuizAttempt gradedAttempt = inProgressAttempt(3);
        gradedAttempt.setStatus(QuizAttemptStatus.GRADED);
        QuizAnswer answered = unanswered();
        answered.setUserAnswerJson("{\"key\":\"B\"}");
        answered.setCorrect(false);
        arrange(gradedAttempt, answered);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "C")
        );

        assertEquals(ErrorCode.ANSWER_ALREADY_SUBMITTED, exception.getErrorCode());
        verify(quizAttemptMapper, never()).gradeAnswerIfUnanswered(any());
    }

    @Test
    void rejectsUnansweredQuestionWhenAttemptAlreadyEnded() {
        QuizAttempt gradedAttempt = inProgressAttempt(3);
        gradedAttempt.setStatus(QuizAttemptStatus.GRADED);
        arrange(gradedAttempt, unanswered());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "B")
        );

        assertEquals(ErrorCode.ATTEMPT_ALREADY_GRADED, exception.getErrorCode());
    }

    @Test
    void rejectsKeyThatDoesNotExistInSnapshotOptions() {
        arrange(inProgressAttempt(3), unanswered());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "D")
        );

        assertEquals(ErrorCode.INVALID_SELECTED_CHOICE, exception.getErrorCode());
        verify(quizAttemptMapper, never()).gradeAnswerIfUnanswered(any());
    }

    @Test
    void completesAttemptAndGrantsRewardWhenLastAnswerIsStored() {
        arrange(inProgressAttempt(3), unanswered());
        when(quizAttemptMapper.gradeAnswerIfUnanswered(any())).thenReturn(1);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID)).thenReturn(3);
        when(quizAttemptMapper.countCorrectByAttemptId(ATTEMPT_ID)).thenReturn(2);
        when(quizRewardService.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                1,
                2,
                NOW
        )).thenReturn(new QuizRewardResult(91L, 200, 7001L));
        when(quizAttemptMapper.completeAttemptIfInProgress(any())).thenReturn(1);

        QuizAnswerGradingResult result = service.grade(
                USER_ID,
                ATTEMPT_ID,
                QUESTION_ID,
                "C"
        );

        assertTrue(result.correct());
        assertTrue(result.allAnswered());
        assertEquals(QuizAttemptStatus.GRADED, result.attemptStatus());
        assertEquals(2, result.correctCount());
        assertEquals(67, result.score());
        assertEquals(200, result.reward().points());
        assertEquals(7001L, result.reward().pointTransactionId());
        assertEquals("NEXT_SUB_CHAPTER", result.nextAction());

        ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(
                QuizAttempt.class
        );
        verify(quizAttemptMapper).completeAttemptIfInProgress(captor.capture());
        assertEquals(QuizAttemptStatus.GRADED, captor.getValue().getStatus());
        assertEquals(2, captor.getValue().getCorrectCount());
        assertEquals(67, captor.getValue().getScore());
        assertEquals(91L, captor.getValue().getRewardPolicyId());
        assertEquals(7001L, captor.getValue().getPointTransactionId());
        assertEquals(NOW, captor.getValue().getSubmittedAt());
    }

    @Test
    void doesNotCompleteAttemptWhenRewardProcessingFails() {
        arrange(inProgressAttempt(3), unanswered());
        when(quizAttemptMapper.gradeAnswerIfUnanswered(any())).thenReturn(1);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID)).thenReturn(3);
        when(quizAttemptMapper.countCorrectByAttemptId(ATTEMPT_ID)).thenReturn(2);
        when(quizRewardService.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                1,
                2,
                NOW
        )).thenThrow(new ApiException(ErrorCode.INTERNAL_ERROR));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.grade(
                        USER_ID,
                        ATTEMPT_ID,
                        QUESTION_ID,
                        "C"
                )
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(quizAttemptMapper, never()).completeAttemptIfInProgress(any());
    }

    @Test
    void completesMainChapterAfterAllAnswersRegardlessOfScore() {
        QuizAttempt attempt = inProgressAttempt(1);
        attempt.setQuizType(QuizType.MAIN_CHAPTER);
        attempt.setMainChapterId(10L);
        attempt.setSubChapterId(null);
        attempt.setContentVersionId(null);
        arrange(attempt, unanswered());
        when(quizAttemptMapper.gradeAnswerIfUnanswered(any())).thenReturn(1);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID)).thenReturn(1);
        when(quizAttemptMapper.countCorrectByAttemptId(ATTEMPT_ID)).thenReturn(0);
        when(quizRewardService.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                1,
                0,
                NOW
        )).thenReturn(new QuizRewardResult(91L, 0, null));
        MainChapterCompletionResult completion =
                new MainChapterCompletionResult(
                        ChapterType.ASSET,
                        true,
                        null
                );
        when(mainChapterCompletionService.complete(USER_ID, 10L, NOW))
                .thenReturn(completion);
        when(quizAttemptMapper.completeAttemptIfInProgress(any())).thenReturn(1);

        QuizAnswerGradingResult result = service.grade(
                USER_ID,
                ATTEMPT_ID,
                QUESTION_ID,
                "B"
        );

        assertFalse(result.correct());
        assertEquals(0, result.correctCount());
        assertEquals(0, result.score());
        assertEquals(completion, result.mainChapterCompletion());
        assertEquals("NEXT_MAIN_CHAPTER", result.nextAction());
        verify(mainChapterCompletionService).complete(USER_ID, 10L, NOW);
    }

    @Test
    void validatesAttemptOwnershipBeforeLookingUpQuestion() {
        QuizAttempt attempt = inProgressAttempt(3);
        attempt.setUserId(99L);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID)).thenReturn(attempt);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "B")
        );

        assertEquals(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN, exception.getErrorCode());
        verify(quizAttemptMapper, never())
                .findAnswerByAttemptIdAndQuestionIdForUpdate(ATTEMPT_ID, QUESTION_ID);
    }

    @Test
    void returnsNotFoundForMissingAttemptAndMissingQuestion() {
        ApiException missingAttempt = assertThrows(
                ApiException.class,
                () -> service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "B")
        );
        assertEquals(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND, missingAttempt.getErrorCode());

        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(inProgressAttempt(3));
        ApiException missingQuestion = assertThrows(
                ApiException.class,
                () -> service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "B")
        );
        assertEquals(ErrorCode.QUESTION_NOT_IN_ATTEMPT, missingQuestion.getErrorCode());
    }

    private void arrange(QuizAttempt attempt, QuizAnswer answer) {
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID)).thenReturn(attempt);
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                QUESTION_ID
        )).thenReturn(answer);
    }

    private QuizAttempt inProgressAttempt(int totalCount) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(ATTEMPT_ID);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.SUB_CHAPTER);
        attempt.setSubChapterId(101L);
        attempt.setContentVersionId(301L);
        attempt.setAttemptNo(1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(totalCount);
        return attempt;
    }

    private QuizAnswer unanswered() {
        QuizAnswer answer = new QuizAnswer();
        answer.setQuizAnswerId(5001L);
        answer.setAttemptId(ATTEMPT_ID);
        answer.setQuestionId(QUESTION_ID);
        answer.setDisplayOrder(1);
        answer.setQuestionSnapshotJson("""
                {
                  "question_type": "SINGLE_CHOICE",
                  "generation_type": "HUMAN",
                  "prompt": "정기예금에 대한 설명으로 알맞은 것은?",
                  "scenario_json": null,
                  "options_json": [
                    {"key":"B","label":"선택지 B"},
                    {"key":"C","label":"선택지 C"}
                  ],
                  "correct_answer_json": {"key":"C"},
                  "explanation": "정기예금 해설"
                }
                """);
        return answer;
    }
}
