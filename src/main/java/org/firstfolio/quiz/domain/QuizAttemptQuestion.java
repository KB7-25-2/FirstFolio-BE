package org.firstfolio.quiz.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

public record QuizAttemptQuestion(
        long questionId,
        int displayOrder,
        QuizQuestionType questionType,
        QuizGenerationType generationType,
        String prompt,
        JsonNode scenario,
        List<QuizChoice> choices
) {
    public QuizAttemptQuestion {
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(generationType, "generationType must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        scenario = scenario == null ? null : scenario.deepCopy();
        choices = List.copyOf(choices);
    }

    @Override
    public JsonNode scenario() {
        return scenario == null ? null : scenario.deepCopy();
    }
}
