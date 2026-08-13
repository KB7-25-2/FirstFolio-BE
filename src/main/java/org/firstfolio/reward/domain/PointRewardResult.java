package org.firstfolio.reward.domain;

public record PointRewardResult(
        long policyId,
        int points,
        Long pointTransactionId
) {
    public PointRewardResult {
        if (policyId <= 0 || points < 0) {
            throw new IllegalArgumentException("invalid point reward result");
        }
        if ((points == 0) != (pointTransactionId == null)) {
            throw new IllegalArgumentException(
                    "point transaction must exist only for positive rewards"
            );
        }
    }
}
