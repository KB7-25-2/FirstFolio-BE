package org.firstfolio.reward.service;

import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.reward.domain.QuizRewardResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class QuizRewardService {

    private static final String POLICY_KEY = "QUIZ_REWARD";
    private static final String REASON_TYPE = "QUIZ";
    private static final String IDEMPOTENCY_PREFIX = "quiz-reward:";

    private final PointRewardService pointRewardService;

    public QuizRewardService(PointRewardService pointRewardService) {
        this.pointRewardService = pointRewardService;
    }

    public QuizRewardResult grantForCompletedAttempt(
            long userId,
            long attemptId,
            int attemptNo,
            int correctCount,
            LocalDateTime completedAt
    ) {
        PointRewardResult reward = pointRewardService.grant(
                userId,
                POLICY_KEY,
                attemptNo == 1 ? correctCount : 0,
                REASON_TYPE,
                attemptId,
                IDEMPOTENCY_PREFIX + attemptId,
                completedAt
        );
        return toQuizReward(reward);
    }

    public QuizRewardResult restore(
            long userId,
            long attemptId,
            Long policyId,
            Long pointTransactionId
    ) {
        return toQuizReward(pointRewardService.restore(
                userId,
                REASON_TYPE,
                attemptId,
                IDEMPOTENCY_PREFIX + attemptId,
                policyId,
                pointTransactionId
        ));
    }

    private QuizRewardResult toQuizReward(PointRewardResult reward) {
        return new QuizRewardResult(
                reward.policyId(),
                reward.points(),
                reward.pointTransactionId()
        );
    }
}
