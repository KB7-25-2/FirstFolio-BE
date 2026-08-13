package org.firstfolio.dailyquest.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyQuestItemTest {

    @Test
    void createsAssignedItemWithoutAnswerOrGradingResult() {
        LocalDateTime assignedAt = LocalDateTime.of(
                2026,
                8,
                13,
                0,
                0
        );

        DailyQuestItem item = DailyQuestItem.assigned(
                100L,
                200L,
                5,
                "{\"prompt\":\"뉴스 상황판단 문제\"}",
                assignedAt
        );

        assertEquals(100L, item.getDailyQuestId());
        assertEquals(200L, item.getQuestionId());
        assertEquals(5, item.getDisplayOrder());
        assertEquals(
                "{\"prompt\":\"뉴스 상황판단 문제\"}",
                item.getQuestionSnapshotJson()
        );
        assertEquals(assignedAt, item.getCreatedAt());
        assertNull(item.getUserAnswerJson());
        assertNull(item.getCorrect());
        assertNull(item.getAnsweredAt());
    }

    @Test
    void rejectsInvalidOrderAndBlankSnapshot() {
        LocalDateTime assignedAt = LocalDateTime.of(
                2026,
                8,
                13,
                0,
                0
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> DailyQuestItem.assigned(
                        100L,
                        200L,
                        0,
                        "{}",
                        assignedAt
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DailyQuestItem.assigned(
                        100L,
                        200L,
                        1,
                        " ",
                        assignedAt
                )
        );
    }
}
