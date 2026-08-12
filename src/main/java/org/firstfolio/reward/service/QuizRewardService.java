package org.firstfolio.reward.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointTransaction;
import org.firstfolio.reward.domain.QuizRewardResult;
import org.firstfolio.reward.domain.RewardPolicy;
import org.firstfolio.reward.mapper.QuizRewardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class QuizRewardService {

    private static final String POLICY_KEY = "QUIZ_REWARD";
    private static final String TRANSACTION_TYPE = "EARN";
    private static final String REASON_TYPE = "QUIZ";
    private static final String IDEMPOTENCY_PREFIX = "quiz-reward:";

    private final QuizRewardMapper quizRewardMapper;
    private final ObjectMapper objectMapper;

    public QuizRewardService(QuizRewardMapper quizRewardMapper) {
        this.quizRewardMapper = quizRewardMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public QuizRewardResult grantForCompletedAttempt(
            long userId,
            long attemptId,
            int attemptNo,
            int correctCount,
            LocalDateTime completedAt
    ) {
        RewardPolicy policy = quizRewardMapper.findActivePolicyAt(
                POLICY_KEY,
                completedAt
        );
        if (policy == null) {
            throw internalError(null);
        }

        int pointsPerCorrect = pointsPerCorrect(policy.getConfigJson());
        int points = attemptNo == 1
                ? multiplyPoints(correctCount, pointsPerCorrect)
                : 0;
        if (points == 0) {
            return new QuizRewardResult(policy.getPolicyId(), 0, null);
        }

        if (quizRewardMapper.increasePointBalance(
                userId,
                points,
                completedAt
        ) != 1) {
            throw internalError(null);
        }
        Integer balanceAfter = quizRewardMapper.findPointBalance(userId);
        if (balanceAfter == null || balanceAfter < points) {
            throw internalError(null);
        }

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(TRANSACTION_TYPE);
        transaction.setAmount(points);
        transaction.setReasonType(REASON_TYPE);
        transaction.setReasonId(attemptId);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setIdempotencyKey(IDEMPOTENCY_PREFIX + attemptId);
        transaction.setOccurredAt(completedAt);
        transaction.setCreatedAt(completedAt);

        if (quizRewardMapper.insertTransaction(transaction) != 1
                || transaction.getPointTransactionId() == null) {
            throw internalError(null);
        }
        return new QuizRewardResult(
                policy.getPolicyId(),
                points,
                transaction.getPointTransactionId()
        );
    }

    @Transactional(readOnly = true)
    public QuizRewardResult restore(
            long userId,
            long attemptId,
            Long policyId,
            Long pointTransactionId
    ) {
        if (policyId == null) {
            throw internalError(null);
        }
        if (pointTransactionId == null) {
            return new QuizRewardResult(policyId, 0, null);
        }

        PointTransaction transaction = quizRewardMapper
                .findTransactionById(pointTransactionId);
        if (transaction == null
                || transaction.getUserId() != userId
                || !TRANSACTION_TYPE.equals(transaction.getTransactionType())
                || !REASON_TYPE.equals(transaction.getReasonType())
                || transaction.getReasonId() == null
                || transaction.getReasonId() != attemptId
                || transaction.getAmount() <= 0) {
            throw internalError(null);
        }
        return new QuizRewardResult(
                policyId,
                transaction.getAmount(),
                transaction.getPointTransactionId()
        );
    }

    private int pointsPerCorrect(String configJson) {
        try {
            JsonNode value = objectMapper
                    .readTree(configJson)
                    .path("points_per_correct");
            if (!value.isIntegralNumber()
                    || !value.canConvertToInt()
                    || value.intValue() < 0) {
                throw internalError(null);
            }
            return value.intValue();
        } catch (JsonProcessingException exception) {
            throw internalError(exception);
        }
    }

    private int multiplyPoints(int correctCount, int pointsPerCorrect) {
        if (correctCount < 0) {
            throw internalError(null);
        }
        try {
            return Math.multiplyExact(correctCount, pointsPerCorrect);
        } catch (ArithmeticException exception) {
            throw internalError(exception);
        }
    }

    private ApiException internalError(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                cause
        );
    }
}
