package org.firstfolio.dailyquest.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.quiz.domain.QuizChoice;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;

import java.util.List;

public record DailyQuestQuestionView(
        long dailyQuestItemId,
        long questionId,
        int displayOrder,
        QuizQuestionType questionType,
        QuizGenerationType generationType,
        String prompt,
        JsonNode scenario,
        List<QuizChoice> choices,
        String savedAnswerKey
) {
    public DailyQuestQuestionView {
        scenario = scenario == null ? null : scenario.deepCopy();
        choices = List.copyOf(choices);
    }

    @Override
    public JsonNode scenario() {
        return scenario == null ? null : scenario.deepCopy();
    }
}
