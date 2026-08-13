package org.firstfolio.dailyquest.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record DailyQuestItemGradingResult(
        long dailyQuestItemId,
        long questionId,
        boolean correct,
        String submittedAnswerKey,
        String correctAnswerKey,
        String explanation,
        JsonNode sourceRefs
) {
    public DailyQuestItemGradingResult {
        if (dailyQuestItemId <= 0 || questionId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (submittedAnswerKey == null || submittedAnswerKey.isBlank()) {
            throw new IllegalArgumentException(
                    "submittedAnswerKey must not be blank"
            );
        }
        if (correctAnswerKey == null || correctAnswerKey.isBlank()) {
            throw new IllegalArgumentException(
                    "correctAnswerKey must not be blank"
            );
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException(
                    "explanation must not be blank"
            );
        }
        sourceRefs = sourceRefs == null ? null : sourceRefs.deepCopy();
    }

    @Override
    public JsonNode sourceRefs() {
        return sourceRefs == null ? null : sourceRefs.deepCopy();
    }
}
