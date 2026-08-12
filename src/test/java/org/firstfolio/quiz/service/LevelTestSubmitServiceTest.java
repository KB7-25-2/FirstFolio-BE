package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestSubmitResult;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTestSubmitServiceTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 2001L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            12,
            3,
            0
    );

    private QuizAttemptMapper quizAttemptMapper;
    private LevelTestSubmitService service;

    @BeforeEach
    void setUp() {
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T03:00:00Z"),
                ZoneOffset.UTC
        );
        service = new LevelTestSubmitService(quizAttemptMapper, clock);
    }

    @Test
    void gradesAllAnswersAndBuildsChapterRecommendationsAtomically() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.IN_PROGRESS, 4);
        List<QuizAnswer> answers = List.of(
                answer(1001L, 1, 2L, AssetType.DEPOSIT_SAVINGS, "B", null),
                answer(1002L, 2, 2L, AssetType.DEPOSIT_SAVINGS, "A", null),
                answer(1003L, 3, 3L, AssetType.BOND, "B", null),
                answer(1004L, 4, 3L, AssetType.BOND, "B", null)
        );
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptIdForUpdate(ATTEMPT_ID))
                .thenReturn(answers);
        when(quizAttemptMapper.gradeLevelTestAnswer(any())).thenReturn(1);
        when(quizAttemptMapper.completeAttemptIfInProgress(any()))
                .thenReturn(1);

        LevelTestSubmitResult result = service.submit(USER_ID, ATTEMPT_ID);

        assertEquals(QuizAttemptStatus.GRADED, result.status());
        assertEquals(List.of(true, false, true, true),
                result.questionResults().stream()
                        .map(question -> question.correct())
                        .toList());
        assertEquals(2, result.chapterResults().size());
        assertEquals(2L, result.chapterResults().get(0).mainChapterId());
        assertEquals(2, result.chapterResults().get(0).totalCount());
        assertEquals(1, result.chapterResults().get(0).correctCount());
        assertFalse(result.chapterResults().get(0).allCorrect());
        assertEquals(3L, result.chapterResults().get(1).mainChapterId());
        assertEquals(2, result.chapterResults().get(1).correctCount());
        assertTrue(result.chapterResults().get(1).allCorrect());

        ArgumentCaptor<QuizAnswer> answerCaptor =
                ArgumentCaptor.forClass(QuizAnswer.class);
        verify(quizAttemptMapper, times(4))
                .gradeLevelTestAnswer(answerCaptor.capture());
        assertEquals(List.of(true, false, true, true),
                answerCaptor.getAllValues().stream()
                        .map(QuizAnswer::getCorrect)
                        .toList());

        ArgumentCaptor<QuizAttempt> attemptCaptor =
                ArgumentCaptor.forClass(QuizAttempt.class);
        verify(quizAttemptMapper).completeAttemptIfInProgress(
                attemptCaptor.capture()
        );
        QuizAttempt completed = attemptCaptor.getValue();
        assertEquals(QuizAttemptStatus.GRADED, completed.getStatus());
        assertEquals(3, completed.getCorrectCount());
        assertEquals(75, completed.getScore());
        assertEquals(NOW, completed.getSubmittedAt());
    }

    @Test
    void rejectsSubmissionWhenAnyAssignedAnswerIsMissing() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.IN_PROGRESS, 2);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptIdForUpdate(ATTEMPT_ID))
                .thenReturn(List.of(
                        answer(1001L, 1, 2L,
                                AssetType.DEPOSIT_SAVINGS, "B", null),
                        answer(1002L, 2, 2L,
                                AssetType.DEPOSIT_SAVINGS, null, null)
                ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID, ATTEMPT_ID)
        );

        assertEquals(ErrorCode.REQUIRED_ANSWERS_MISSING,
                exception.getErrorCode());
        verify(quizAttemptMapper, never()).gradeLevelTestAnswer(any());
        verify(quizAttemptMapper, never()).completeAttemptIfInProgress(any());
    }

    @Test
    void returnsFirstConfirmedResultForRepeatedSubmission() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.GRADED, 2);
        attempt.setCorrectCount(1);
        attempt.setScore(50);
        attempt.setSubmittedAt(NOW.minusMinutes(10));
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptIdForUpdate(ATTEMPT_ID))
                .thenReturn(List.of(
                        answer(1001L, 1, 2L,
                                AssetType.DEPOSIT_SAVINGS, "B", true),
                        answer(1002L, 2, 2L,
                                AssetType.DEPOSIT_SAVINGS, "A", false)
                ));

        LevelTestSubmitResult result = service.submit(USER_ID, ATTEMPT_ID);

        assertEquals(QuizAttemptStatus.GRADED, result.status());
        assertEquals(List.of(true, false), result.questionResults().stream()
                .map(question -> question.correct())
                .toList());
        assertFalse(result.chapterResults().get(0).allCorrect());
        verify(quizAttemptMapper, never()).gradeLevelTestAnswer(any());
        verify(quizAttemptMapper, never()).completeAttemptIfInProgress(any());
    }

    @Test
    void rejectsMissingForeignOrNonLevelTestAttempt() {
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(null);
        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID, ATTEMPT_ID)
        );
        assertEquals(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND,
                missing.getErrorCode());

        QuizAttempt foreign = attempt(QuizAttemptStatus.IN_PROGRESS, 1);
        foreign.setUserId(99L);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(foreign);
        ApiException forbidden = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID, ATTEMPT_ID)
        );
        assertEquals(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN,
                forbidden.getErrorCode());

        QuizAttempt regular = attempt(QuizAttemptStatus.IN_PROGRESS, 1);
        regular.setQuizType(QuizType.SUB_CHAPTER);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(regular);
        ApiException wrongType = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID, ATTEMPT_ID)
        );
        assertEquals(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND,
                wrongType.getErrorCode());
        verify(quizAttemptMapper, never())
                .findAnswersByAttemptIdForUpdate(anyLong());
    }

    @Test
    void rejectsUnfinishedSubmittedState() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.SUBMITTED, 1);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptIdForUpdate(ATTEMPT_ID))
                .thenReturn(List.of(
                        answer(1001L, 1, 2L,
                                AssetType.DEPOSIT_SAVINGS, "B", null)
                ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submit(USER_ID, ATTEMPT_ID)
        );

        assertEquals(ErrorCode.ATTEMPT_ALREADY_GRADED,
                exception.getErrorCode());
    }

    private QuizAttempt attempt(QuizAttemptStatus status, int totalCount) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(ATTEMPT_ID);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.LEVEL_TEST);
        attempt.setAttemptNo(1);
        attempt.setStatus(status);
        attempt.setTotalCount(totalCount);
        return attempt;
    }

    private QuizAnswer answer(
            long questionId,
            int displayOrder,
            long mainChapterId,
            AssetType assetType,
            String selectedKey,
            Boolean correct
    ) {
        QuizAnswer answer = new QuizAnswer();
        answer.setQuizAnswerId(questionId + 5000L);
        answer.setAttemptId(ATTEMPT_ID);
        answer.setQuestionId(questionId);
        answer.setDisplayOrder(displayOrder);
        answer.setQuestionSnapshotJson(snapshot(
                questionId,
                mainChapterId,
                assetType
        ));
        answer.setUserAnswerJson(selectedKey == null
                ? null
                : "{\"key\":\"" + selectedKey + "\"}");
        answer.setCorrect(correct);
        answer.setAnsweredAt(NOW.minusMinutes(20));
        return answer;
    }

    private String snapshot(
            long questionId,
            long mainChapterId,
            AssetType assetType
    ) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(questionId);
        question.setMainChapterId(mainChapterId);
        question.setQuestionType(QuizQuestionType.SINGLE_CHOICE);
        question.setGenerationType(QuizGenerationType.HUMAN);
        question.setPrompt("레벨 테스트 문제");
        question.setOptionsJson("""
                [
                  {"key":"A","label":"선택지 A"},
                  {"key":"B","label":"선택지 B"}
                ]
                """);
        question.setCorrectAnswerJson("{\"key\":\"B\"}");
        question.setExplanation("정답 해설");

        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(mainChapterId);
        chapter.setAssetType(assetType);
        return new LevelTestQuestionSnapshotCodec().createSnapshot(
                question,
                chapter
        );
    }
}
