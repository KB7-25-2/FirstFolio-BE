package org.firstfolio.dailyquest.domain;

import java.util.List;
import java.util.Objects;

public record DailyQuestTodayResult(
        DailyQuest dailyQuest,
        int answeredCount,
        List<DailyQuestQuestionView> questions
) {
    public DailyQuestTodayResult {
        Objects.requireNonNull(dailyQuest, "dailyQuest must not be null");
        questions = List.copyOf(questions);
        if (dailyQuest.getTotalCount() != questions.size()) {
            throw new IllegalArgumentException(
                    "questions must match the daily quest total count"
            );
        }
        if (answeredCount < 0 || answeredCount > questions.size()) {
            throw new IllegalArgumentException(
                    "answeredCount must be within the question count"
            );
        }
    }
}
