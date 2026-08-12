package org.firstfolio.quiz.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.curriculum.domain.AssetType;

import java.util.List;

public record LevelTestAttemptQuestion(
        long questionId,
        int displayOrder,
        long mainChapterId,
        AssetType assetType,
        QuizQuestionType questionType,
        QuizGenerationType generationType,
        String prompt,
        JsonNode scenario,
        List<QuizChoice> choices
) {
    public LevelTestAttemptQuestion {
        scenario = scenario == null ? null : scenario.deepCopy();
        choices = List.copyOf(choices);
    }

    @Override
    public JsonNode scenario() {
        return scenario == null ? null : scenario.deepCopy();
    }
}
