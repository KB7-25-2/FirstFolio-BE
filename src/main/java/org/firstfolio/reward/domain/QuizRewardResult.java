package org.firstfolio.reward.domain;

public record QuizRewardResult(
        long policyId,
        int points,
        Long pointTransactionId
) {
    public QuizRewardResult {
        if (policyId <= 0 || points < 0) {
            throw new IllegalArgumentException("invalid quiz reward result");
        }
        if (points > 0 && pointTransactionId == null) {
            throw new IllegalArgumentException(
                    "positive reward must include point transaction"
            );
        }
    }
}
