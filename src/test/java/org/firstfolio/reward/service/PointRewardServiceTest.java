package org.firstfolio.reward.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.reward.domain.PointTransaction;
import org.firstfolio.reward.domain.RewardPolicy;
import org.firstfolio.reward.mapper.PointRewardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointRewardServiceTest {

    private static final long USER_ID = 11L;
    private static final long REASON_ID = 4001L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            13,
            1,
            30
    );

    private PointRewardMapper mapper;
    private PointRewardService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PointRewardMapper.class);
        service = new PointRewardService(mapper);
    }

    @Test
    void grantsConfiguredPointsAndWritesDomainLedgerIdentity() {
        when(mapper.findActivePolicyAt("DAILY_QUEST_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":100}"));
        when(mapper.increasePointBalance(USER_ID, 400, NOW)).thenReturn(1);
        when(mapper.findPointBalance(USER_ID)).thenReturn(1400);
        when(mapper.insertTransaction(any())).thenAnswer(invocation -> {
            PointTransaction transaction = invocation.getArgument(0);
            transaction.setPointTransactionId(9001L);
            return 1;
        });

        PointRewardResult result = grant(4);

        assertEquals(91L, result.policyId());
        assertEquals(400, result.points());
        assertEquals(9001L, result.pointTransactionId());

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(
                PointTransaction.class
        );
        verify(mapper).insertTransaction(captor.capture());
        PointTransaction transaction = captor.getValue();
        assertEquals("EARN", transaction.getTransactionType());
        assertEquals("DAILY_QUEST", transaction.getReasonType());
        assertEquals(REASON_ID, transaction.getReasonId());
        assertEquals(1400, transaction.getBalanceAfter());
        assertEquals(
                "daily-quest-reward:4001",
                transaction.getIdempotencyKey()
        );
        assertEquals(NOW, transaction.getOccurredAt());
        assertEquals(NOW, transaction.getCreatedAt());
    }

    @Test
    void recordsPolicyWithoutZeroAmountLedger() {
        when(mapper.findActivePolicyAt("DAILY_QUEST_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":100}"));

        PointRewardResult result = grant(0);

        assertEquals(91L, result.policyId());
        assertEquals(0, result.points());
        assertNull(result.pointTransactionId());
        verify(mapper, never()).increasePointBalance(USER_ID, 0, NOW);
        verify(mapper, never()).insertTransaction(any());
    }

    @Test
    void restoresOnlyMatchingPersistedLedger() {
        PointTransaction transaction = new PointTransaction();
        transaction.setPointTransactionId(9001L);
        transaction.setUserId(USER_ID);
        transaction.setTransactionType("EARN");
        transaction.setReasonType("DAILY_QUEST");
        transaction.setReasonId(REASON_ID);
        transaction.setAmount(400);
        transaction.setIdempotencyKey("daily-quest-reward:4001");
        when(mapper.findTransactionById(9001L)).thenReturn(transaction);

        PointRewardResult result = service.restore(
                USER_ID,
                "DAILY_QUEST",
                REASON_ID,
                "daily-quest-reward:4001",
                91L,
                9001L
        );

        assertEquals(400, result.points());
        assertEquals(9001L, result.pointTransactionId());
    }

    @Test
    void rejectsMissingOrInvalidPolicy() {
        ApiException missing = assertThrows(
                ApiException.class,
                () -> grant(4)
        );
        assertEquals(ErrorCode.INTERNAL_ERROR, missing.getErrorCode());

        when(mapper.findActivePolicyAt("DAILY_QUEST_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":-1}"));
        ApiException invalid = assertThrows(
                ApiException.class,
                () -> grant(4)
        );
        assertEquals(ErrorCode.INTERNAL_ERROR, invalid.getErrorCode());
    }

    private PointRewardResult grant(int correctCount) {
        return service.grant(
                USER_ID,
                "DAILY_QUEST_REWARD",
                correctCount,
                "DAILY_QUEST",
                REASON_ID,
                "daily-quest-reward:4001",
                NOW
        );
    }

    private RewardPolicy policy(String configJson) {
        RewardPolicy policy = new RewardPolicy();
        policy.setPolicyId(91L);
        policy.setPolicyKey("DAILY_QUEST_REWARD");
        policy.setVersionNo(1);
        policy.setConfigJson(configJson);
        return policy;
    }
}
