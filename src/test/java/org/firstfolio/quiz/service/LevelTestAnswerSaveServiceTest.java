package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveCommand;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveResult;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTestAnswerSaveServiceTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 2001L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            12,
            2,
            0
    );

    private QuizAttemptMapper quizAttemptMapper;
    private LevelTestAnswerSaveService service;

    @BeforeEach
    void setUp() {
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T02:00:00Z"),
                ZoneOffset.UTC
        );
        service = new LevelTestAnswerSaveService(quizAttemptMapper, clock);
    }

    @Test
    void savesPartialAnswersAndOverwritesExistingSelectionWithoutGrading() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.IN_PROGRESS, 3);
        QuizAnswer first = answer(1001L, 1, "{\"key\":\"A\"}");
        QuizAnswer second = answer(1002L, 2, null);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                1001L
        )).thenReturn(first);
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                1002L
        )).thenReturn(second);
        when(quizAttemptMapper.saveLevelTestAnswer(any())).thenReturn(1);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID))
                .thenReturn(2);

        LevelTestAnswerSaveResult result = service.save(
                USER_ID,
                ATTEMPT_ID,
                List.of(
                        new LevelTestAnswerSaveCommand(1001L, " B "),
                        new LevelTestAnswerSaveCommand(1002L, "A")
                )
        );

        assertEquals(ATTEMPT_ID, result.attemptId());
        assertEquals(2, result.savedAnswerCount());
        assertEquals(2, result.answeredCount());
        assertEquals(3, result.totalCount());
        assertEquals(QuizAttemptStatus.IN_PROGRESS, result.status());
        assertEquals(NOW, result.updatedAt());

        ArgumentCaptor<QuizAnswer> captor =
                ArgumentCaptor.forClass(QuizAnswer.class);
        verify(quizAttemptMapper, times(2))
                .saveLevelTestAnswer(captor.capture());
        assertEquals("{\"key\":\"B\"}",
                captor.getAllValues().get(0).getUserAnswerJson());
        assertEquals("{\"key\":\"A\"}",
                captor.getAllValues().get(1).getUserAnswerJson());
        for (QuizAnswer saved : captor.getAllValues()) {
            assertNull(saved.getCorrect());
            assertEquals(NOW, saved.getAnsweredAt());
        }
    }

    @Test
    void keepsAttemptInProgressWhenLastAnswerIsSaved() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.IN_PROGRESS, 1);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                1001L
        )).thenReturn(answer(1001L, 1, null));
        when(quizAttemptMapper.saveLevelTestAnswer(any())).thenReturn(1);
        when(quizAttemptMapper.countAnsweredByAttemptId(ATTEMPT_ID))
                .thenReturn(1);

        LevelTestAnswerSaveResult result = service.save(
                USER_ID,
                ATTEMPT_ID,
                List.of(new LevelTestAnswerSaveCommand(1001L, "B"))
        );

        assertEquals(QuizAttemptStatus.IN_PROGRESS, result.status());
        assertEquals(1, result.answeredCount());
        assertEquals(1, result.totalCount());
        verify(quizAttemptMapper, never())
                .completeAttemptIfInProgress(any());
    }

    @Test
    void rejectsEmptyOrDuplicateRequestBeforeLockingAttempt() {
        ApiException empty = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, ATTEMPT_ID, List.of())
        );
        assertEquals(ErrorCode.INVALID_REQUEST, empty.getErrorCode());

        ApiException duplicate = assertThrows(
                ApiException.class,
                () -> service.save(
                        USER_ID,
                        ATTEMPT_ID,
                        List.of(
                                new LevelTestAnswerSaveCommand(1001L, "A"),
                                new LevelTestAnswerSaveCommand(1001L, "B")
                        )
                )
        );
        assertEquals(ErrorCode.INVALID_REQUEST, duplicate.getErrorCode());
        verify(quizAttemptMapper, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void rejectsMissingOrForeignAttempt() {
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(null);
        ApiException missing = assertThrows(
                ApiException.class,
                () -> saveOne()
        );
        assertEquals(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND, missing.getErrorCode());

        QuizAttempt foreign = attempt(QuizAttemptStatus.IN_PROGRESS, 1);
        foreign.setUserId(99L);
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(foreign);
        ApiException forbidden = assertThrows(
                ApiException.class,
                () -> saveOne()
        );
        assertEquals(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN,
                forbidden.getErrorCode());
    }

    @Test
    void rejectsCompletedAttempt() {
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt(QuizAttemptStatus.GRADED, 1));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> saveOne()
        );

        assertEquals(ErrorCode.ATTEMPT_ALREADY_GRADED,
                exception.getErrorCode());
        verify(quizAttemptMapper, never())
                .findAnswerByAttemptIdAndQuestionIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void rejectsQuestionOutsideAttempt() {
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt(QuizAttemptStatus.IN_PROGRESS, 1));
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                1001L
        )).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> saveOne()
        );

        assertEquals(ErrorCode.QUESTION_NOT_IN_ATTEMPT,
                exception.getErrorCode());
        verify(quizAttemptMapper, never()).saveLevelTestAnswer(any());
    }

    @Test
    void rejectsInvalidChoiceWithoutSavingAnyAnswerInBatch() {
        when(quizAttemptMapper.findByIdForUpdate(ATTEMPT_ID))
                .thenReturn(attempt(QuizAttemptStatus.IN_PROGRESS, 2));
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                1001L
        )).thenReturn(answer(1001L, 1, null));
        when(quizAttemptMapper.findAnswerByAttemptIdAndQuestionIdForUpdate(
                ATTEMPT_ID,
                1002L
        )).thenReturn(answer(1002L, 2, null));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.save(
                        USER_ID,
                        ATTEMPT_ID,
                        List.of(
                                new LevelTestAnswerSaveCommand(1001L, "A"),
                                new LevelTestAnswerSaveCommand(1002L, "Z")
                        )
                )
        );

        assertEquals(ErrorCode.INVALID_SELECTED_CHOICE,
                exception.getErrorCode());
        verify(quizAttemptMapper, never()).saveLevelTestAnswer(any());
    }

    private LevelTestAnswerSaveResult saveOne() {
        return service.save(
                USER_ID,
                ATTEMPT_ID,
                List.of(new LevelTestAnswerSaveCommand(1001L, "B"))
        );
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
            String userAnswerJson
    ) {
        QuizAnswer answer = new QuizAnswer();
        answer.setQuizAnswerId(questionId + 5000L);
        answer.setAttemptId(ATTEMPT_ID);
        answer.setQuestionId(questionId);
        answer.setDisplayOrder(displayOrder);
        answer.setQuestionSnapshotJson(snapshot(questionId));
        answer.setUserAnswerJson(userAnswerJson);
        answer.setCorrect(null);
        return answer;
    }

    private String snapshot(long questionId) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(questionId);
        question.setMainChapterId(2L);
        question.setQuestionType(QuizQuestionType.SINGLE_CHOICE);
        question.setGenerationType(QuizGenerationType.HUMAN);
        question.setPrompt("예금 문제");
        question.setOptionsJson("""
                [
                  {"key":"A","label":"선택지 A"},
                  {"key":"B","label":"선택지 B"}
                ]
                """);
        question.setCorrectAnswerJson("{\"key\":\"B\"}");
        question.setExplanation("정답 해설");

        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(2L);
        chapter.setAssetType(AssetType.DEPOSIT_SAVINGS);
        return new LevelTestQuestionSnapshotCodec().createSnapshot(
                question,
                chapter
        );
    }
}
