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
        List<QuizChoice> choices,
        boolean answered,
        String selectedKey,
        Boolean correct,
        String correctKey,
        String explanation
) {
    public QuizAttemptQuestion {
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(generationType, "generationType must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        scenario = scenario == null ? null : scenario.deepCopy();
        choices = List.copyOf(choices);
        if (answered && (selectedKey == null
                || correct == null
                || correctKey == null
                || explanation == null)) {
            throw new IllegalArgumentException(
                    "answered question must include its grading result"
            );
        }
        if (!answered && (selectedKey != null
                || correct != null
                || correctKey != null
                || explanation != null)) {
            throw new IllegalArgumentException(
                    "unanswered question must not include a grading result"
            );
        }
    }

    public QuizAttemptQuestion(
            long questionId,
            int displayOrder,
            QuizQuestionType questionType,
            QuizGenerationType generationType,
            String prompt,
            JsonNode scenario,
            List<QuizChoice> choices
    ) {
        this(
                questionId,
                displayOrder,
                questionType,
                generationType,
                prompt,
                scenario,
                choices,
                false,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public JsonNode scenario() {
        return scenario == null ? null : scenario.deepCopy();
    }
}
