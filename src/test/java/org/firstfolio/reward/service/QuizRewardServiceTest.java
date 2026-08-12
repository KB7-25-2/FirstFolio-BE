package org.firstfolio.reward.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointTransaction;
import org.firstfolio.reward.domain.QuizRewardResult;
import org.firstfolio.reward.domain.RewardPolicy;
import org.firstfolio.reward.mapper.QuizRewardMapper;
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

class QuizRewardServiceTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 3001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 1, 30);

    private QuizRewardMapper mapper;
    private QuizRewardService service;

    @BeforeEach
    void setUp() {
        mapper = mock(QuizRewardMapper.class);
        service = new QuizRewardService(mapper);
    }

    @Test
    void grantsPointsPerCorrectForFirstAttemptAndWritesLedger() {
        when(mapper.findActivePolicyAt("QUIZ_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":100}"));
        when(mapper.increasePointBalance(USER_ID, 200, NOW)).thenReturn(1);
        when(mapper.findPointBalance(USER_ID)).thenReturn(1200);
        when(mapper.insertTransaction(any())).thenAnswer(invocation -> {
            PointTransaction transaction = invocation.getArgument(0);
            transaction.setPointTransactionId(7001L);
            return 1;
        });

        QuizRewardResult result = service.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                1,
                2,
                NOW
        );

        assertEquals(91L, result.policyId());
        assertEquals(200, result.points());
        assertEquals(7001L, result.pointTransactionId());

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(
                PointTransaction.class
        );
        verify(mapper).insertTransaction(captor.capture());
        PointTransaction transaction = captor.getValue();
        assertEquals("EARN", transaction.getTransactionType());
        assertEquals("QUIZ", transaction.getReasonType());
        assertEquals(ATTEMPT_ID, transaction.getReasonId());
        assertEquals(1200, transaction.getBalanceAfter());
        assertEquals("quiz-reward:3001", transaction.getIdempotencyKey());
        assertEquals(NOW, transaction.getOccurredAt());
    }

    @Test
    void doesNotGrantPointsForRetryAttempt() {
        when(mapper.findActivePolicyAt("QUIZ_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":100}"));

        QuizRewardResult result = service.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                2,
                3,
                NOW
        );

        assertEquals(0, result.points());
        assertNull(result.pointTransactionId());
        verify(mapper, never()).increasePointBalance(
                USER_ID,
                300,
                NOW
        );
        verify(mapper, never()).insertTransaction(any());
    }

    @Test
    void recordsPolicyButDoesNotCreateZeroAmountTransaction() {
        when(mapper.findActivePolicyAt("QUIZ_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":100}"));

        QuizRewardResult result = service.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                1,
                0,
                NOW
        );

        assertEquals(91L, result.policyId());
        assertEquals(0, result.points());
        assertNull(result.pointTransactionId());
        verify(mapper, never()).insertTransaction(any());
    }

    @Test
    void restoresPersistedRewardForIdempotentAnswerRequest() {
        PointTransaction transaction = new PointTransaction();
        transaction.setPointTransactionId(7001L);
        transaction.setUserId(USER_ID);
        transaction.setTransactionType("EARN");
        transaction.setReasonType("QUIZ");
        transaction.setReasonId(ATTEMPT_ID);
        transaction.setAmount(200);
        when(mapper.findTransactionById(7001L)).thenReturn(transaction);

        QuizRewardResult result = service.restore(
                USER_ID,
                ATTEMPT_ID,
                91L,
                7001L
        );

        assertEquals(91L, result.policyId());
        assertEquals(200, result.points());
        assertEquals(7001L, result.pointTransactionId());
    }

    @Test
    void rejectsMissingOrInvalidRewardPolicy() {
        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.grantForCompletedAttempt(
                        USER_ID,
                        ATTEMPT_ID,
                        1,
                        2,
                        NOW
                )
        );
        assertEquals(ErrorCode.INTERNAL_ERROR, missing.getErrorCode());

        when(mapper.findActivePolicyAt("QUIZ_REWARD", NOW))
                .thenReturn(policy("{\"points_per_correct\":-1}"));
        ApiException invalid = assertThrows(
                ApiException.class,
                () -> service.grantForCompletedAttempt(
                        USER_ID,
                        ATTEMPT_ID,
                        1,
                        2,
                        NOW
                )
        );
        assertEquals(ErrorCode.INTERNAL_ERROR, invalid.getErrorCode());
    }

    private RewardPolicy policy(String configJson) {
        RewardPolicy policy = new RewardPolicy();
        policy.setPolicyId(91L);
        policy.setPolicyKey("QUIZ_REWARD");
        policy.setVersionNo(1);
        policy.setConfigJson(configJson);
        return policy;
    }
}
