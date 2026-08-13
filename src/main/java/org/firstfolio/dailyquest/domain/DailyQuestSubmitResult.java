package org.firstfolio.dailyquest.domain;

import org.firstfolio.reward.domain.PointRewardResult;

import java.time.LocalDateTime;
import java.util.List;

public record DailyQuestSubmitResult(
        long dailyQuestId,
        DailyQuestStatus status,
        int correctCount,
        int score,
        PointRewardResult reward,
        List<DailyQuestItemGradingResult> results,
        LocalDateTime completedAt
) {
    public DailyQuestSubmitResult {
        if (dailyQuestId <= 0
                || status != DailyQuestStatus.COMPLETED
                || correctCount < 0
                || correctCount > DailyQuest.TOTAL_QUESTION_COUNT
                || score != correctCount
                || reward == null
                || results == null
                || results.size() != DailyQuest.TOTAL_QUESTION_COUNT
                || completedAt == null) {
            throw new IllegalArgumentException(
                    "invalid daily quest submit result"
            );
        }
        results = List.copyOf(results);
    }
}
