package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.integration.ai.dto.request.QuizCorrectAnswerRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizOptionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchItemRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizScenarioRequest;
import org.firstfolio.quiz.integration.ai.dto.response.QuizQuestionBatchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizQuestionBatchServiceTest {

    private QuizQuestionPayloadValidator payloadValidator;
    private QuizChapterScopeValidator chapterScopeValidator;
    private QuizQuestionBatchWriter writer;
    private QuizQuestionBatchService service;

    @BeforeEach
    void setUp() {
        payloadValidator = mock(QuizQuestionPayloadValidator.class);
        chapterScopeValidator = mock(QuizChapterScopeValidator.class);
        writer = mock(QuizQuestionBatchWriter.class);
        service = new QuizQuestionBatchService(
                new QuizQuestionBatchStructureValidator(),
                payloadValidator,
                chapterScopeValidator,
                writer
        );
    }

    @Test
    void skipsChapterScopeValidationForDailyNews() {
        QuizQuestionRequest quiz = dailyNewsQuiz(LocalDate.of(2026, 8, 6));
        when(payloadValidator.validate(quiz)).thenReturn(QuizItemValidationResult.valid());
        when(writer.saveAll(List.of(quiz))).thenReturn(List.of(savedQuestion(quiz)));

        QuizQuestionBatchResponse response = service.process(batchOf(quiz));

        verify(chapterScopeValidator, never()).validate(any());
        assertEquals(1, response.accepted());
        assertEquals(0, response.rejected());
    }

    @Test
    void stillRunsChapterScopeValidationForSubChapter() {
        QuizQuestionRequest quiz = subChapterQuiz();
        when(payloadValidator.validate(quiz)).thenReturn(QuizItemValidationResult.valid());
        when(chapterScopeValidator.validate(quiz)).thenReturn(QuizItemValidationResult.valid());
        when(writer.saveAll(List.of(quiz))).thenReturn(List.of(savedQuestion(quiz)));

        QuizQuestionBatchResponse response = service.process(batchOf(quiz));

        verify(chapterScopeValidator).validate(quiz);
        assertEquals(1, response.accepted());
    }

    private QuizQuestionBatchRequest batchOf(QuizQuestionRequest quiz) {
        return new QuizQuestionBatchRequest(
                UUID.randomUUID().toString(),
                List.of(new QuizQuestionBatchItemRequest(UUID.randomUUID().toString(), quiz))
        );
    }

    private QuizQuestion savedQuestion(QuizQuestionRequest quiz) {
        QuizQuestion question = QuizQuestion.draft(
                UUID.randomUUID().toString(),
                1,
                quiz.usageType(),
                quiz.mainChapterId(),
                quiz.subChapterId(),
                null,
                quiz.questionType(),
                quiz.difficulty(),
                quiz.prompt(),
                null,
                "[]",
                "{\"key\":\"1\"}",
                quiz.explanation(),
                QuizGenerationType.AI,
                quiz.questDate(),
                null,
                1L,
                LocalDateTime.of(2026, 8, 19, 6, 0)
        );
        question.setQuestionId(1L);
        question.setStatus(QuizQuestionStatus.REVIEW);
        return question;
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
