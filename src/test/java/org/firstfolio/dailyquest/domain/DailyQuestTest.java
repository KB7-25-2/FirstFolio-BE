package org.firstfolio.dailyquest.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyQuestTest {

    @Test
    void createsAssignedFiveQuestionQuest() {
        LocalDate questDate = LocalDate.of(2026, 8, 13);

        DailyQuest dailyQuest = DailyQuest.assigned(10L, questDate);

        assertEquals(10L, dailyQuest.getUserId());
        assertEquals(questDate, dailyQuest.getQuestDate());
        assertEquals(DailyQuestStatus.ASSIGNED, dailyQuest.getStatus());
        assertEquals(5, dailyQuest.getTotalCount());
        assertEquals(0, dailyQuest.getCorrectCount());
        assertEquals(0, dailyQuest.getScore());
        assertNull(dailyQuest.getRewardPolicyId());
        assertNull(dailyQuest.getPointTransactionId());
        assertNull(dailyQuest.getCompletedAt());
    }

    @Test
    void rejectsInvalidAssignmentIdentityAndDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DailyQuest.assigned(0L, LocalDate.of(2026, 8, 13))
        );
        assertThrows(
                NullPointerException.class,
                () -> DailyQuest.assigned(10L, null)
        );
    }
}
