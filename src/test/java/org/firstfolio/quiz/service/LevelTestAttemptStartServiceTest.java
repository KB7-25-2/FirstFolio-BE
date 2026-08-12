package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAttemptStartResult;
import org.firstfolio.quiz.domain.LevelTestQuestionSet;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.domain.QuizUsageType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTestAttemptStartServiceTest {

    private static final long USER_ID = 11L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            12,
            1,
            0
    );

    private QuizAttemptMapper quizAttemptMapper;
    private LevelTestQueryService levelTestQueryService;
    private LevelTestAttemptStartService service;

    @BeforeEach
    void setUp() {
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        levelTestQueryService = mock(LevelTestQueryService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T01:00:00Z"),
                ZoneOffset.UTC
        );
        service = new LevelTestAttemptStartService(
                quizAttemptMapper,
                levelTestQueryService,
                clock
        );
        when(quizAttemptMapper.findUserIdForUpdate(USER_ID))
                .thenReturn(USER_ID);
    }

    @Test
    void createsOneIntegratedAttemptAndQuestionSnapshots() {
        MainChapter deposit = chapter(
                2L,
                AssetType.DEPOSIT_SAVINGS,
                1
        );
        MainChapter bond = chapter(3L, AssetType.BOND, 2);
        List<QuizQuestion> questions = List.of(
                question(1001L, 2L, 1, "예금 문제"),
                question(1002L, 2L, 2, "적금 문제"),
                question(1003L, 3L, 1, "채권 문제")
        );
        when(quizAttemptMapper.findLevelTestByUserIdForUpdate(USER_ID))
                .thenReturn(null);
        when(levelTestQueryService.getQuestionSet()).thenReturn(
                new LevelTestQuestionSet(
                        List.of(deposit, bond),
                        questions
                )
        );
        when(quizAttemptMapper.insertAttempt(any())).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(2001L);
            return 1;
        });
        when(quizAttemptMapper.insertAnswer(any())).thenReturn(1);

        LevelTestAttemptStartResult result = service.start(USER_ID);

        assertEquals(2001L, result.attempt().getAttemptId());
        assertEquals(QuizType.LEVEL_TEST, result.attempt().getQuizType());
        assertEquals(QuizAttemptStatus.IN_PROGRESS,
                result.attempt().getStatus());
        assertEquals(1, result.attempt().getAttemptNo());
        assertEquals(3, result.attempt().getTotalCount());
        assertNull(result.attempt().getMainChapterId());
        assertNull(result.attempt().getSubChapterId());
        assertNull(result.attempt().getContentVersionId());
        assertEquals(NOW, result.attempt().getStartedAt());
        assertEquals(List.of(1001L, 1002L, 1003L),
                result.questions().stream()
                        .map(question -> question.questionId())
                        .toList());
        assertEquals(List.of(1, 2, 3), result.questions().stream()
                .map(question -> question.displayOrder())
                .toList());
        assertEquals(List.of(
                        AssetType.DEPOSIT_SAVINGS,
                        AssetType.DEPOSIT_SAVINGS,
                        AssetType.BOND
                ),
                result.questions().stream()
                        .map(question -> question.assetType())
                        .toList());
        assertTrue(result.answers().isEmpty());

        ArgumentCaptor<QuizAnswer> answerCaptor =
                ArgumentCaptor.forClass(QuizAnswer.class);
        verify(quizAttemptMapper, times(3))
                .insertAnswer(answerCaptor.capture());
        QuizAnswer first = answerCaptor.getAllValues().get(0);
        assertEquals(2001L, first.getAttemptId());
        assertEquals(1001L, first.getQuestionId());
        assertEquals(1, first.getDisplayOrder());
        assertEquals(NOW, first.getCreatedAt());
        assertNull(first.getUserAnswerJson());
        assertNull(first.getCorrect());
        assertTrue(first.getQuestionSnapshotJson().contains(
                "\"main_chapter_id\":2"
        ));
        assertTrue(first.getQuestionSnapshotJson().contains(
                "\"correct_answer_json\""
        ));
    }

    @Test
    void restoresInProgressAttemptWithSavedAnswers() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.IN_PROGRESS, 2);
        MainChapter deposit = chapter(
                2L,
                AssetType.DEPOSIT_SAVINGS,
                1
        );
        QuizAnswer first = snapshotAnswer(
                attempt.getAttemptId(),
                1,
                question(1001L, 2L, 1, "예금 문제"),
                deposit,
                "{\"key\":\"B\"}"
        );
        QuizAnswer second = snapshotAnswer(
                attempt.getAttemptId(),
                2,
                question(1002L, 2L, 2, "적금 문제"),
                deposit,
                null
        );
        when(quizAttemptMapper.findLevelTestByUserIdForUpdate(USER_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptId(2001L))
                .thenReturn(List.of(first, second));

        LevelTestAttemptStartResult result = service.start(USER_ID);

        assertEquals(List.of(1001L, 1002L), result.questions().stream()
                .map(question -> question.questionId())
                .toList());
        assertEquals(1, result.answers().size());
        assertEquals(1001L, result.answers().get(0).questionId());
        assertEquals("B", result.answers().get(0).key());
        verify(levelTestQueryService, never()).getQuestionSet();
        verify(quizAttemptMapper, never()).insertAttempt(any());
        verify(quizAttemptMapper, never()).insertAnswer(any());
    }

    @Test
    void rejectsCompletedAttemptWithoutCreatingAnotherOne() {
        when(quizAttemptMapper.findLevelTestByUserIdForUpdate(USER_ID))
                .thenReturn(attempt(QuizAttemptStatus.GRADED, 2));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.start(USER_ID)
        );

        assertEquals(
                ErrorCode.LEVEL_TEST_ALREADY_COMPLETED,
                exception.getErrorCode()
        );
        verify(levelTestQueryService, never()).getQuestionSet();
        verify(quizAttemptMapper, never()).insertAttempt(any());
    }

    @Test
    void rejectsBrokenRestoredQuestionSet() {
        QuizAttempt attempt = attempt(QuizAttemptStatus.IN_PROGRESS, 2);
        when(quizAttemptMapper.findLevelTestByUserIdForUpdate(USER_ID))
                .thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptId(2001L))
                .thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.start(USER_ID)
        );

        assertEquals(
                ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID,
                exception.getErrorCode()
        );
    }

    private MainChapter chapter(
            long id,
            AssetType assetType,
            int displayOrder
    ) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setChapterType(ChapterType.ASSET);
        chapter.setAssetType(assetType);
        chapter.setDisplayOrder(displayOrder);
        chapter.setActive(true);
        return chapter;
    }

    private QuizQuestion question(
            long id,
            long mainChapterId,
            int displayOrder,
            String prompt
    ) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(id);
        question.setQuestionKey("level-test-" + id);
        question.setVersionNo(1);
        question.setUsageType(QuizUsageType.LEVEL_TEST);
        question.setMainChapterId(mainChapterId);
        question.setDisplayOrder(displayOrder);
        question.setQuestionType(QuizQuestionType.SINGLE_CHOICE);
        question.setGenerationType(QuizGenerationType.HUMAN);
        question.setPrompt(prompt);
        question.setOptionsJson("""
                [
                  {"key":"A","label":"선택지 A"},
                  {"key":"B","label":"선택지 B"}
                ]
                """);
        question.setCorrectAnswerJson("{\"key\":\"B\"}");
        question.setExplanation("정답 해설");
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private QuizAttempt attempt(QuizAttemptStatus status, int totalCount) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(2001L);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.LEVEL_TEST);
        attempt.setAttemptNo(1);
        attempt.setStatus(status);
        attempt.setTotalCount(totalCount);
        return attempt;
    }

    private QuizAnswer snapshotAnswer(
            long attemptId,
            int displayOrder,
            QuizQuestion question,
            MainChapter chapter,
            String userAnswerJson
    ) {
        QuizAnswer answer = new QuizAnswer();
        answer.setAttemptId(attemptId);
        answer.setQuestionId(question.getQuestionId());
        answer.setDisplayOrder(displayOrder);
        answer.setQuestionSnapshotJson(
                new LevelTestQuestionSnapshotCodec().createSnapshot(
                        question,
                        chapter
                )
        );
        answer.setUserAnswerJson(userAnswerJson);
        answer.setCreatedAt(NOW);
        return answer;
    }
}
