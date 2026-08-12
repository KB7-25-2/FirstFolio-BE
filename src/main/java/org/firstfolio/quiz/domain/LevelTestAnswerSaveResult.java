package org.firstfolio.quiz.domain;

import java.time.LocalDateTime;

public record LevelTestAnswerSaveResult(
        long attemptId,
        int savedAnswerCount,
        int answeredCount,
        int totalCount,
        QuizAttemptStatus status,
        LocalDateTime updatedAt
) {
}
