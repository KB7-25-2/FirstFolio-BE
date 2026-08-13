package org.firstfolio.dailyquest.domain;

public record DailyQuestAnswerSaveResult(
        long dailyQuestId,
        long dailyQuestItemId,
        String savedAnswerKey,
        int answeredCount,
        int totalCount
) {
    public DailyQuestAnswerSaveResult {
        if (dailyQuestId <= 0 || dailyQuestItemId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (savedAnswerKey == null || savedAnswerKey.isBlank()) {
            throw new IllegalArgumentException(
                    "savedAnswerKey must not be blank"
            );
        }
        if (totalCount <= 0
                || answeredCount < 0
                || answeredCount > totalCount) {
            throw new IllegalArgumentException(
                    "answer counts must be valid"
            );
        }
    }
}
