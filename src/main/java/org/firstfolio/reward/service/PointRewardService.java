package org.firstfolio.reward.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.reward.domain.PointTransaction;
import org.firstfolio.reward.domain.RewardPolicy;
import org.firstfolio.reward.mapper.PointRewardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PointRewardService {

    private static final String TRANSACTION_TYPE = "EARN";

    private final PointRewardMapper pointRewardMapper;
    private final ObjectMapper objectMapper;

    public PointRewardService(PointRewardMapper pointRewardMapper) {
        this.pointRewardMapper = pointRewardMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public PointRewardResult grant(
            long userId,
            String policyKey,
            int rewardUnits,
            String reasonType,
            long reasonId,
            String idempotencyKey,
            LocalDateTime occurredAt
    ) {
        validateSource(
                userId,
                policyKey,
                rewardUnits,
                reasonType,
                reasonId,
                idempotencyKey,
                occurredAt
        );
        RewardPolicy policy = pointRewardMapper.findActivePolicyAt(
                policyKey,
                occurredAt
        );
        validatePolicy(policy, policyKey);

        int points = multiplyPoints(
                rewardUnits,
                pointsPerCorrect(policy.getConfigJson())
        );
        if (points == 0) {
            return new PointRewardResult(policy.getPolicyId(), 0, null);
        }

        if (pointRewardMapper.increasePointBalance(
                userId,
                points,
                occurredAt
        ) != 1) {
            throw internalError(null);
        }
        Integer balanceAfter = pointRewardMapper.findPointBalance(userId);
        if (balanceAfter == null || balanceAfter < points) {
            throw internalError(null);
        }

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(TRANSACTION_TYPE);
        transaction.setAmount(points);
        transaction.setReasonType(reasonType);
        transaction.setReasonId(reasonId);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setOccurredAt(occurredAt);
        transaction.setCreatedAt(occurredAt);

        if (pointRewardMapper.insertTransaction(transaction) != 1
                || transaction.getPointTransactionId() == null) {
            throw internalError(null);
        }
        return new PointRewardResult(
                policy.getPolicyId(),
                points,
                transaction.getPointTransactionId()
        );
    }

    @Transactional(readOnly = true)
    public PointRewardResult restore(
            long userId,
            String reasonType,
            long reasonId,
            String idempotencyKey,
            Long policyId,
            Long pointTransactionId
    ) {
        validateRewardIdentity(
                userId,
                reasonType,
                reasonId,
                idempotencyKey
        );
        if (policyId == null || policyId <= 0) {
            throw internalError(null);
        }
        if (pointTransactionId == null) {
            return new PointRewardResult(policyId, 0, null);
        }

        PointTransaction transaction = pointRewardMapper
                .findTransactionById(pointTransactionId);
        if (transaction == null
                || transaction.getPointTransactionId() == null
                || transaction.getPointTransactionId().longValue()
                    != pointTransactionId.longValue()
                || transaction.getUserId() != userId
                || !TRANSACTION_TYPE.equals(transaction.getTransactionType())
                || !reasonType.equals(transaction.getReasonType())
                || transaction.getReasonId() == null
                || transaction.getReasonId() != reasonId
                || !idempotencyKey.equals(transaction.getIdempotencyKey())
                || transaction.getAmount() <= 0) {
            throw internalError(null);
        }
        return new PointRewardResult(
                policyId,
                transaction.getAmount(),
                transaction.getPointTransactionId()
        );
    }

    private void validateSource(
            long userId,
            String policyKey,
            int rewardUnits,
            String reasonType,
            long reasonId,
            String idempotencyKey,
            LocalDateTime occurredAt
    ) {
        validateRewardIdentity(
                userId,
                reasonType,
                reasonId,
                idempotencyKey
        );
        if (policyKey == null
                || policyKey.isBlank()
                || policyKey.length() > 50
                || rewardUnits < 0
                || occurredAt == null) {
            throw internalError(null);
        }
    }

    private void validateRewardIdentity(
            long userId,
            String reasonType,
            long reasonId,
            String idempotencyKey
    ) {
        if (userId <= 0
                || reasonType == null
                || reasonType.isBlank()
                || reasonType.length() > 30
                || reasonId <= 0
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 120) {
            throw internalError(null);
        }
    }

    private void validatePolicy(RewardPolicy policy, String policyKey) {
        if (policy == null
                || policy.getPolicyId() <= 0
                || !policyKey.equals(policy.getPolicyKey())
                || policy.getVersionNo() <= 0
                || policy.getConfigJson() == null) {
            throw internalError(null);
        }
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

    private int multiplyPoints(int rewardUnits, int pointsPerUnit) {
        try {
            return Math.multiplyExact(rewardUnits, pointsPerUnit);
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
