package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.learning.mapper.MainChapterLearningMapper;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStartResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainChapterQuizAttemptStartServiceTest {

    private static final long USER_ID = 11L;
    private static final long MAIN_CHAPTER_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 2, 0);

    private QuizAttemptMapper attemptMapper;
    private QuizQuestionMapper questionMapper;
    private MainChapterLearningMapper learningMapper;
    private MainChapterMapper mainChapterMapper;
    private MainChapterQuizAttemptStartService service;

    @BeforeEach
    void setUp() {
        attemptMapper = mock(QuizAttemptMapper.class);
        questionMapper = mock(QuizQuestionMapper.class);
        learningMapper = mock(MainChapterLearningMapper.class);
        mainChapterMapper = mock(MainChapterMapper.class);
        service = new MainChapterQuizAttemptStartService(
                attemptMapper,
                questionMapper,
                learningMapper,
                mainChapterMapper,
                Clock.fixed(
                        Instant.parse("2026-08-11T02:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        when(learningMapper.findActiveCurriculumItemForUpdate(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(curriculumItem());
        when(mainChapterMapper.findById(MAIN_CHAPTER_ID))
                .thenReturn(mainChapter());
    }

    @Test
    void startsWithAllLatestPublishedMainChapterQuestions() {
        when(learningMapper.countActiveSubChapters(MAIN_CHAPTER_ID))
                .thenReturn(2);
        when(learningMapper.countIncompleteActiveSubChapters(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(0);
        when(questionMapper.findLatestPublishedByMainChapterId(MAIN_CHAPTER_ID))
                .thenReturn(List.of(question(1001L), question(1002L)));
        when(attemptMapper.findMaxAttemptNoByUserIdAndMainChapterId(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(1);
        when(attemptMapper.insertAttempt(any())).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(3001L);
            return 1;
        });
        when(attemptMapper.insertAnswer(any())).thenReturn(1);

        QuizAttemptStartResult result = service.start(USER_ID, MAIN_CHAPTER_ID);

        assertEquals(QuizType.MAIN_CHAPTER, result.attempt().getQuizType());
        assertEquals(MAIN_CHAPTER_ID, result.attempt().getMainChapterId());
        assertNull(result.attempt().getSubChapterId());
        assertNull(result.attempt().getContentVersionId());
        assertEquals(2, result.attempt().getAttemptNo());
        assertEquals(2, result.questions().size());
        assertEquals(1001L, result.questions().get(0).questionId());
        assertEquals(2, result.questions().get(1).displayOrder());

        ArgumentCaptor<QuizAnswer> answers = ArgumentCaptor.forClass(
                QuizAnswer.class
        );
        verify(attemptMapper, org.mockito.Mockito.times(2))
                .insertAnswer(answers.capture());
        assertEquals(NOW, answers.getAllValues().get(0).getCreatedAt());
        assertEquals(QuizGenerationType.HUMAN,
                result.questions().get(0).generationType());
    }

    @Test
    void restoresExistingInProgressAttemptBeforeCheckingCompletionAgain() {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(3001L);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.MAIN_CHAPTER);
        attempt.setMainChapterId(MAIN_CHAPTER_ID);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(1);
        when(attemptMapper.findInProgressByUserIdAndMainChapterIdForUpdate(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(attempt);
        when(attemptMapper.findAnswersByAttemptId(3001L))
                .thenReturn(List.of(snapshotAnswer()));

        QuizAttemptStartResult result = service.start(USER_ID, MAIN_CHAPTER_ID);

        assertEquals(3001L, result.attempt().getAttemptId());
        assertEquals(1, result.questions().size());
        verify(learningMapper, never()).countActiveSubChapters(MAIN_CHAPTER_ID);
        verify(attemptMapper, never()).insertAttempt(any());
    }

    @Test
    void rejectsWhenAnyActiveSubChapterIsIncomplete() {
        when(learningMapper.countActiveSubChapters(MAIN_CHAPTER_ID))
                .thenReturn(2);
        when(learningMapper.countIncompleteActiveSubChapters(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.start(USER_ID, MAIN_CHAPTER_ID)
        );

        assertEquals(ErrorCode.SUB_CHAPTERS_INCOMPLETE, exception.getErrorCode());
        verify(questionMapper, never())
                .findLatestPublishedByMainChapterId(MAIN_CHAPTER_ID);
    }

    @Test
    void rejectsUnavailableCurriculumOrQuestionSet() {
        when(learningMapper.findActiveCurriculumItemForUpdate(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(null);
        ApiException missingCurriculum = assertThrows(
                ApiException.class,
                () -> service.start(USER_ID, MAIN_CHAPTER_ID)
        );
        assertEquals(ErrorCode.QUIZ_NOT_AVAILABLE,
                missingCurriculum.getErrorCode());
    }

    private UserCurriculumItem curriculumItem() {
        UserCurriculumItem item = new UserCurriculumItem();
        item.setCurriculumItemId(100L);
        item.setUserId(USER_ID);
        item.setMainChapterId(MAIN_CHAPTER_ID);
        item.setStatus(CurriculumItemStatus.ACTIVE);
        return item;
    }

    private MainChapter mainChapter() {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(MAIN_CHAPTER_ID);
        chapter.setChapterType(ChapterType.ASSET);
        chapter.setActive(true);
        return chapter;
    }

    private QuizQuestion question(long questionId) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(questionId);
        question.setUsageType(QuizUsageType.MAIN_CHAPTER);
        question.setMainChapterId(MAIN_CHAPTER_ID);
        question.setDisplayOrder((int) (questionId - 1000));
        question.setQuestionType(QuizQuestionType.SCENARIO);
        question.setPrompt("금리 변화 시나리오 문항");
        question.setScenarioJson("{\"market\":\"금리 상승\"}");
        question.setOptionsJson("[{\"key\":\"A\",\"label\":\"채권 가격 하락\"}]");
        question.setCorrectAnswerJson("{\"key\":\"A\"}");
        question.setExplanation("금리와 채권 가격은 반대로 움직입니다.");
        question.setGenerationType(QuizGenerationType.HUMAN);
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private QuizAnswer snapshotAnswer() {
        QuizAnswer answer = new QuizAnswer();
        answer.setAttemptId(3001L);
        answer.setQuestionId(1001L);
        answer.setDisplayOrder(1);
        answer.setQuestionSnapshotJson("""
                {
                  "question_type":"SCENARIO",
                  "generation_type":"HUMAN",
                  "prompt":"시나리오 문항",
                  "scenario_json":{"market":"금리 상승"},
                  "options_json":[{"key":"A","label":"채권 가격 하락"}],
                  "correct_answer_json":{"key":"A"},
                  "explanation":"해설"
                }
                """);
        return answer;
    }
}
