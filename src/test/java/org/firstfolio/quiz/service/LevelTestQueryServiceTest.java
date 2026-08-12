package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAttemptAggregate;
import org.firstfolio.quiz.domain.LevelTestQuestionSet;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTestQueryServiceTest {

    private MainChapterMapper mainChapterMapper;
    private QuizQuestionMapper quizQuestionMapper;
    private QuizAttemptMapper quizAttemptMapper;
    private LevelTestQueryService service;

    @BeforeEach
    void setUp() {
        mainChapterMapper = mock(MainChapterMapper.class);
        quizQuestionMapper = mock(QuizQuestionMapper.class);
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        service = new LevelTestQueryService(
                mainChapterMapper,
                quizQuestionMapper,
                quizAttemptMapper
        );
    }

    @Test
    void acceptsDifferentQuestionCountsPerActiveAssetChapter() {
        MainChapter deposit = mainChapter(2L, AssetType.DEPOSIT_SAVINGS);
        MainChapter bond = mainChapter(3L, AssetType.BOND);
        List<QuizQuestion> questions = List.of(
                question(1001L, 2L),
                question(1002L, 2L),
                question(1003L, 3L)
        );
        when(mainChapterMapper.findAll(ChapterType.ASSET, true))
                .thenReturn(List.of(deposit, bond));
        when(quizQuestionMapper.findLatestPublishedLevelTestQuestions())
                .thenReturn(questions);

        LevelTestQuestionSet result = service.getQuestionSet();

        assertEquals(List.of(deposit, bond), result.mainChapters());
        assertEquals(questions, result.questions());
    }

    @Test
    void rejectsQuestionSetWhenAnActiveAssetHasNoQuestion() {
        when(mainChapterMapper.findAll(ChapterType.ASSET, true))
                .thenReturn(List.of(
                        mainChapter(2L, AssetType.DEPOSIT_SAVINGS),
                        mainChapter(3L, AssetType.BOND)
                ));
        when(quizQuestionMapper.findLatestPublishedLevelTestQuestions())
                .thenReturn(List.of(question(1001L, 2L)));

        ApiException exception = assertThrows(
                ApiException.class,
                service::getQuestionSet
        );

        assertEquals(
                ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID,
                exception.getErrorCode()
        );
    }

    @Test
    void returnsNullWhenUserHasNoLevelTestAttempt() {
        when(quizAttemptMapper.findLevelTestByUserId(11L)).thenReturn(null);

        assertNull(service.findAttempt(11L));
        verify(quizAttemptMapper, never()).findAnswersByAttemptId(0L);
    }

    @Test
    void restoresOneIntegratedAttemptWithAssignedAnswers() {
        QuizAttempt attempt = integratedAttempt(3001L, 2);
        QuizAnswer first = answer(3001L, 1001L);
        QuizAnswer second = answer(3001L, 1002L);
        when(quizAttemptMapper.findLevelTestByUserId(11L)).thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptId(3001L))
                .thenReturn(List.of(first, second));

        LevelTestAttemptAggregate result = service.findAttempt(11L);

        assertSame(attempt, result.attempt());
        assertEquals(List.of(first, second), result.answers());
    }

    @Test
    void rejectsAttemptWhoseAssignedAnswerCountChanged() {
        QuizAttempt attempt = integratedAttempt(3001L, 2);
        when(quizAttemptMapper.findLevelTestByUserId(11L)).thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptId(3001L))
                .thenReturn(List.of(answer(3001L, 1001L)));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findAttempt(11L)
        );

        assertEquals(
                ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID,
                exception.getErrorCode()
        );
    }

    private MainChapter mainChapter(long id, AssetType assetType) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setChapterType(ChapterType.ASSET);
        chapter.setAssetType(assetType);
        chapter.setActive(true);
        return chapter;
    }

    private QuizQuestion question(long id, long mainChapterId) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(id);
        question.setUsageType(QuizUsageType.LEVEL_TEST);
        question.setMainChapterId(mainChapterId);
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private QuizAttempt integratedAttempt(long id, int totalCount) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(id);
        attempt.setUserId(11L);
        attempt.setQuizType(QuizType.LEVEL_TEST);
        attempt.setTotalCount(totalCount);
        return attempt;
    }

    private QuizAnswer answer(long attemptId, long questionId) {
        QuizAnswer answer = new QuizAnswer();
        answer.setAttemptId(attemptId);
        answer.setQuestionId(questionId);
        return answer;
    }
}
