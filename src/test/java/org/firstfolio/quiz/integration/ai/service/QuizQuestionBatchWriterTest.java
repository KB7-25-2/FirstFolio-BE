package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.integration.ai.dto.request.QuizCorrectAnswerRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizOptionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizScenarioRequest;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizQuestionBatchWriterTest {

    private QuizQuestionMapper questionMapper;
    private QuizQuestionBatchWriter writer;

    @BeforeEach
    void setUp() {
        questionMapper = mock(QuizQuestionMapper.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("quiz.batch.ai-created-by")).thenReturn("1");
        writer = new QuizQuestionBatchWriter(
                questionMapper,
                environment,
                Clock.fixed(Instant.parse("2026-08-19T06:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void leavesDisplayOrderNullForSubChapterQuestions() {
        List<QuizQuestion> saved = writer.saveAll(List.of(subChapterQuiz()));

        assertNull(saved.get(0).getDisplayOrder());
    }

    @Test
    void assignsFirstDisplayOrderWhenNoExistingMainChapterQuestions() {
        when(questionMapper.findMaxDisplayOrderByMainChapterId(2L)).thenReturn(null);

        List<QuizQuestion> saved = writer.saveAll(List.of(mainChapterQuiz(2L)));

        assertEquals(1, saved.get(0).getDisplayOrder());
    }

    @Test
    void continuesFromExistingMaxDisplayOrder() {
        when(questionMapper.findMaxDisplayOrderByMainChapterId(2L)).thenReturn(5);

        List<QuizQuestion> saved = writer.saveAll(List.of(mainChapterQuiz(2L)));

        assertEquals(6, saved.get(0).getDisplayOrder());
    }

    @Test
    void incrementsSequentiallyWithinSameBatchForSameChapter() {
        when(questionMapper.findMaxDisplayOrderByMainChapterId(2L)).thenReturn(5);

        List<QuizQuestion> saved = writer.saveAll(
                List.of(mainChapterQuiz(2L), mainChapterQuiz(2L))
        );

        assertEquals(6, saved.get(0).getDisplayOrder());
        assertEquals(7, saved.get(1).getDisplayOrder());
        verify(questionMapper, times(1)).findMaxDisplayOrderByMainChapterId(2L);
    }

    @Test
    void tracksDisplayOrderIndependentlyPerMainChapter() {
        when(questionMapper.findMaxDisplayOrderByMainChapterId(2L)).thenReturn(null);
        when(questionMapper.findMaxDisplayOrderByMainChapterId(3L)).thenReturn(10);

        List<QuizQuestion> saved = writer.saveAll(
                List.of(mainChapterQuiz(2L), mainChapterQuiz(3L))
        );

        assertEquals(1, saved.get(0).getDisplayOrder());
        assertEquals(11, saved.get(1).getDisplayOrder());
    }

    @Test
    void savesQuestDateForDailyNewsQuestions() {
        LocalDate questDate = LocalDate.of(2026, 8, 6);

        List<QuizQuestion> saved = writer.saveAll(List.of(dailyNewsQuiz(questDate)));

        assertEquals(questDate, saved.get(0).getQuestDate());
        assertNull(saved.get(0).getDisplayOrder());
    }

    @Test
    void leavesQuestDateNullForSubChapterQuestions() {
        List<QuizQuestion> saved = writer.saveAll(List.of(subChapterQuiz()));

        assertNull(saved.get(0).getQuestDate());
    }

    private QuizQuestionRequest subChapterQuiz() {
        return new QuizQuestionRequest(
                QuizUsageType.SUB_CHAPTER,
                2L,
                101L,
                QuizQuestionType.TRUE_FALSE,
                QuizDifficulty.EASY,
                "예금은 원금이 보장된다.",
                null,
                List.of(
                        new QuizOptionRequest("O", "O", null),
                        new QuizOptionRequest("X", "X", null)
                ),
                new QuizCorrectAnswerRequest("O"),
                "해설",
                null,
                null
        );
    }

    private QuizQuestionRequest mainChapterQuiz(long mainChapterId) {
        return new QuizQuestionRequest(
                QuizUsageType.MAIN_CHAPTER,
                mainChapterId,
                null,
                QuizQuestionType.SCENARIO,
                QuizDifficulty.MEDIUM,
                "시나리오 프롬프트",
                scenario(),
                List.of(
                        new QuizOptionRequest("1", "선택지1", null),
                        new QuizOptionRequest("2", "선택지2", null),
                        new QuizOptionRequest("3", "선택지3", null),
                        new QuizOptionRequest("4", "선택지4", null)
                ),
                new QuizCorrectAnswerRequest("1"),
                "해설",
                null,
                null
        );
    }

    private QuizQuestionRequest dailyNewsQuiz(LocalDate questDate) {
        return new QuizQuestionRequest(
                QuizUsageType.DAILY_NEWS,
                null,
                null,
                QuizQuestionType.SCENARIO,
                QuizDifficulty.MEDIUM,
                "뉴스 시나리오 프롬프트",
                scenario(),
                List.of(
                        new QuizOptionRequest("1", "선택지1", null),
                        new QuizOptionRequest("2", "선택지2", null),
                        new QuizOptionRequest("3", "선택지3", null),
                        new QuizOptionRequest("4", "선택지4", null)
                ),
                new QuizCorrectAnswerRequest("1"),
                "해설",
                null,
                questDate
        );
    }

    private QuizScenarioRequest scenario() {
        return new QuizScenarioRequest(
                "제목",
                "내러티브",
                new QuizScenarioRequest.Persona("홍길동", "30대", "회사원"),
                new QuizScenarioRequest.Requirements("자산", "중립", "목표"),
                new QuizScenarioRequest.Market("시장", "2026-08-19T00:00:00Z", List.of("불릿")),
                List.of("제약"),
                null
        );
    }
}
