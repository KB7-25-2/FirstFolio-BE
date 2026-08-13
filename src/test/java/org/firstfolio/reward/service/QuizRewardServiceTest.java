package org.firstfolio.reward.service;

import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.reward.domain.QuizRewardResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizRewardServiceTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 3001L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            11,
            1,
            30
    );

    private PointRewardService pointRewardService;
    private QuizRewardService service;

    @BeforeEach
    void setUp() {
        pointRewardService = mock(PointRewardService.class);
        service = new QuizRewardService(pointRewardService);
    }

    @Test
    void mapsFirstAttemptToQuizRewardIdentity() {
        when(pointRewardService.grant(
                USER_ID,
                "QUIZ_REWARD",
                2,
                "QUIZ",
                ATTEMPT_ID,
                "quiz-reward:3001",
                NOW
        )).thenReturn(new PointRewardResult(91L, 200, 7001L));

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
    }

    @Test
    void mapsRetryAttemptToZeroRewardUnits() {
        when(pointRewardService.grant(
                USER_ID,
                "QUIZ_REWARD",
                0,
                "QUIZ",
                ATTEMPT_ID,
                "quiz-reward:3001",
                NOW
        )).thenReturn(new PointRewardResult(91L, 0, null));

        QuizRewardResult result = service.grantForCompletedAttempt(
                USER_ID,
                ATTEMPT_ID,
                2,
                3,
                NOW
        );

        assertEquals(0, result.points());
        assertNull(result.pointTransactionId());
    }

    @Test
    void restoresQuizRewardThroughCommonLedgerIdentity() {
        when(pointRewardService.restore(
                USER_ID,
                "QUIZ",
                ATTEMPT_ID,
                "quiz-reward:3001",
                91L,
                7001L
        )).thenReturn(new PointRewardResult(91L, 200, 7001L));

        QuizRewardResult result = service.restore(
                USER_ID,
                ATTEMPT_ID,
                91L,
                7001L
        );

        assertEquals(200, result.points());
        assertEquals(7001L, result.pointTransactionId());
        verify(pointRewardService).restore(
                USER_ID,
                "QUIZ",
                ATTEMPT_ID,
                "quiz-reward:3001",
                91L,
                7001L
        );
    }
}
