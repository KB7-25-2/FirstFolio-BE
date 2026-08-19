package org.firstfolio.quiz.integration.ai.dto.request;

import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;

import java.util.List;

public record QuizQuestionRequest(
        QuizUsageType usageType,
        Long mainChapterId,
        Long subChapterId,
        QuizQuestionType questionType,
        QuizDifficulty difficulty,
        String prompt,
        QuizScenarioRequest scenarioJson,
        List<QuizOptionRequest> optionsJson,
        QuizCorrectAnswerRequest correctAnswerJson,
        String explanation,
        Object sourceRefsJson
) {
}
