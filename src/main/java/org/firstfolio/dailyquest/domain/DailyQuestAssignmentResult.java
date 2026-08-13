package org.firstfolio.dailyquest.domain;

import java.util.List;
import java.util.Objects;

public record DailyQuestAssignmentResult(
        DailyQuest dailyQuest,
        List<DailyQuestItem> items
) {
    public DailyQuestAssignmentResult {
        Objects.requireNonNull(dailyQuest, "dailyQuest must not be null");
        items = List.copyOf(items);
    }
}
