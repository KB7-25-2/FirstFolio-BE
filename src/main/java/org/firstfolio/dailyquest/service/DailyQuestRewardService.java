package org.firstfolio.dailyquest.service;

import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.reward.service.PointRewardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DailyQuestRewardService {

    private static final String POLICY_KEY = "DAILY_QUEST_REWARD";
    private static final String REASON_TYPE = "DAILY_QUEST";
    private static final String IDEMPOTENCY_PREFIX = "daily-quest-reward:";

    private final PointRewardService pointRewardService;

    public DailyQuestRewardService(PointRewardService pointRewardService) {
        this.pointRewardService = pointRewardService;
    }

    public PointRewardResult grant(
            long userId,
            long dailyQuestId,
            int correctCount,
            LocalDateTime completedAt
    ) {
        return pointRewardService.grant(
                userId,
                POLICY_KEY,
                correctCount,
                REASON_TYPE,
                dailyQuestId,
                idempotencyKey(dailyQuestId),
                completedAt
        );
    }

    public PointRewardResult restore(
            long userId,
            long dailyQuestId,
            Long rewardPolicyId,
            Long pointTransactionId
    ) {
        return pointRewardService.restore(
                userId,
                REASON_TYPE,
                dailyQuestId,
                idempotencyKey(dailyQuestId),
                rewardPolicyId,
                pointTransactionId
        );
    }

    private String idempotencyKey(long dailyQuestId) {
        return IDEMPOTENCY_PREFIX + dailyQuestId;
    }
}
