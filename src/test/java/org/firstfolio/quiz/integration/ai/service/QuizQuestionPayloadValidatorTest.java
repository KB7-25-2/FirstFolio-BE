package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.integration.ai.dto.request.QuizCorrectAnswerRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizOptionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizScenarioRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizQuestionPayloadValidatorTest {

    private final QuizQuestionPayloadValidator validator = new QuizQuestionPayloadValidator();

    @Test
    void acceptsSubChapterTrueFalseWithoutQuestDate() {
        QuizItemValidationResult result = validator.validate(
                subChapterQuiz(QuizQuestionType.TRUE_FALSE)
        );

        assertTrue(result.isValid());
    }

    @Test
    void acceptsMainChapterScenarioWithoutQuestDate() {
        QuizItemValidationResult result = validator.validate(
                mainChapterQuiz()
        );

        assertTrue(result.isValid());
    }

    @Test
    void acceptsDailyGeneralTrueFalseWithSubChapterId() {
        QuizItemValidationResult result = validator.validate(
                dailyGeneralQuiz(QuizQuestionType.TRUE_FALSE)
        );

        assertTrue(result.isValid());
    }

    @Test
    void rejectsDailyGeneralWithoutSubChapterId() {
        QuizQuestionRequest base = dailyGeneralQuiz(QuizQuestionType.SINGLE_CHOICE);
        QuizQuestionRequest quiz = new QuizQuestionRequest(
                base.usageType(),
                base.mainChapterId(),
                null,
                base.questionType(),
                base.difficulty(),
                base.prompt(),
                base.scenarioJson(),
                base.optionsJson(),
                base.correctAnswerJson(),
                base.explanation(),
                base.sourceRefsJson(),
                base.questDate()
        );

        QuizItemValidationResult result = validator.validate(quiz);

        assertEquals(QuizItemErrorCode.INVALID_CHAPTER_SCOPE, result.errorCode());
    }

    @Test
    void acceptsDailyNewsScenarioWithQuestDate() {
        QuizItemValidationResult result = validator.validate(
                dailyNewsQuiz(LocalDate.of(2026, 8, 6))
        );

        assertTrue(result.isValid());
    }

    @Test
    void rejectsDailyNewsWithoutQuestDate() {
        QuizItemValidationResult result = validator.validate(
                dailyNewsQuiz(null)
        );

        assertEquals(QuizItemErrorCode.INVALID_QUEST_DATE, result.errorCode());
    }

    @Test
    void rejectsSubChapterWithQuestDate() {
        QuizQuestionRequest quiz = new QuizQuestionRequest(
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
                LocalDate.of(2026, 8, 6)
        );

        QuizItemValidationResult result = validator.validate(quiz);

        assertEquals(QuizItemErrorCode.INVALID_QUEST_DATE, result.errorCode());
    }

    @Test
    void rejectsDailyNewsWithMainChapterId() {
        QuizQuestionRequest base = dailyNewsQuiz(LocalDate.of(2026, 8, 6));
        QuizQuestionRequest quiz = new QuizQuestionRequest(
                base.usageType(),
                2L,
                base.subChapterId(),
                base.questionType(),
                base.difficulty(),
                base.prompt(),
                base.scenarioJson(),
                base.optionsJson(),
                base.correctAnswerJson(),
                base.explanation(),
                base.sourceRefsJson(),
                base.questDate()
        );

        QuizItemValidationResult result = validator.validate(quiz);

        assertEquals(QuizItemErrorCode.INVALID_CHAPTER_SCOPE, result.errorCode());
    }

    @Test
    void rejectsScenarioQuestionTypeWithSubChapterUsageType() {
        QuizQuestionRequest base = mainChapterQuiz();
        QuizQuestionRequest quiz = new QuizQuestionRequest(
                QuizUsageType.SUB_CHAPTER,
                base.mainChapterId(),
                null,
                base.questionType(),
                base.difficulty(),
                base.prompt(),
                base.scenarioJson(),
                base.optionsJson(),
                base.correctAnswerJson(),
                base.explanation(),
                base.sourceRefsJson(),
                base.questDate()
        );

        QuizItemValidationResult result = validator.validate(quiz);

        assertEquals(QuizItemErrorCode.INVALID_USAGE_TYPE, result.errorCode());
    }

    private QuizQuestionRequest subChapterQuiz(QuizQuestionType questionType) {
        return new QuizQuestionRequest(
                QuizUsageType.SUB_CHAPTER,
                2L,
                101L,
                questionType,
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

    private QuizQuestionRequest mainChapterQuiz() {
        return new QuizQuestionRequest(
                QuizUsageType.MAIN_CHAPTER,
                2L,
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

    private QuizQuestionRequest dailyGeneralQuiz(QuizQuestionType questionType) {
        return new QuizQuestionRequest(
                QuizUsageType.DAILY_GENERAL,
                2L,
                101L,
                questionType,
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
