package org.firstfolio.quiz.domain;

import org.firstfolio.learning.domain.MainChapterCompletionResult;
import org.firstfolio.reward.domain.QuizRewardResult;

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
        boolean allAnswered,
        Integer correctCount,
        Integer score,
        QuizRewardResult reward,
        MainChapterCompletionResult mainChapterCompletion,
        String nextAction
) {
    public QuizAnswerGradingResult {
        Objects.requireNonNull(generationType, "generationType must not be null");
        Objects.requireNonNull(selectedKey, "selectedKey must not be null");
        Objects.requireNonNull(correctKey, "correctKey must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        Objects.requireNonNull(attemptStatus, "attemptStatus must not be null");
        if (attemptStatus == QuizAttemptStatus.GRADED
                && (correctCount == null || score == null || reward == null)) {
            throw new IllegalArgumentException(
                    "graded result must include score and reward"
            );
        }
        if (attemptStatus != QuizAttemptStatus.GRADED
                && mainChapterCompletion != null) {
            throw new IllegalArgumentException(
                    "in-progress result must not include main chapter completion"
            );
        }
    }
}
