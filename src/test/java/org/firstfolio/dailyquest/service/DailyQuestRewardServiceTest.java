package org.firstfolio.dailyquest.service;

import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.reward.service.PointRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestRewardServiceTest {

    private PointRewardService pointRewardService;
    private DailyQuestRewardService service;

    @BeforeEach
    void setUp() {
        pointRewardService = mock(PointRewardService.class);
        service = new DailyQuestRewardService(pointRewardService);
    }

    @Test
    void grantsWithDailyQuestPolicyAndLedgerIdentity() {
        LocalDateTime completedAt = LocalDateTime.of(
                2026,
                8,
                13,
                1,
                30
        );
        PointRewardResult reward = new PointRewardResult(
                91L,
                400,
                9001L
        );
        when(pointRewardService.grant(
                10L,
                "DAILY_QUEST_REWARD",
                4,
                "DAILY_QUEST",
                4001L,
                "daily-quest-reward:4001",
                completedAt
        )).thenReturn(reward);

        assertSame(reward, service.grant(10L, 4001L, 4, completedAt));
    }

    @Test
    void restoresWithSameIdempotencyIdentity() {
        PointRewardResult reward = new PointRewardResult(
                91L,
                400,
                9001L
        );
        when(pointRewardService.restore(
                10L,
                "DAILY_QUEST",
                4001L,
                "daily-quest-reward:4001",
                91L,
                9001L
        )).thenReturn(reward);

        assertSame(reward, service.restore(
                10L,
                4001L,
                91L,
                9001L
        ));
        verify(pointRewardService).restore(
                10L,
                "DAILY_QUEST",
                4001L,
                "daily-quest-reward:4001",
                91L,
                9001L
        );
    }
}
