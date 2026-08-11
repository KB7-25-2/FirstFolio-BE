package org.firstfolio.quiz.domain;

import java.util.Objects;

public record QuizAnswerGradingResult(
        long attemptId,
        long questionId,
        QuizGenerationType generationType,
        String selectedKey,
        boolean correct,
        String correctKey,
        String explanation,
        QuizAttemptStatus attemptStatus,
        int answeredCount,
        int totalCount,
        boolean allAnswered
) {
    public QuizAnswerGradingResult {
        Objects.requireNonNull(generationType, "generationType must not be null");
        Objects.requireNonNull(selectedKey, "selectedKey must not be null");
        Objects.requireNonNull(correctKey, "correctKey must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        Objects.requireNonNull(attemptStatus, "attemptStatus must not be null");
    }
}
