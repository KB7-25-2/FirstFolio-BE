package org.firstfolio.learning.domain;

import java.util.Objects;

public record LearningProgressStatusResult(
        LearningProgress progress,
        SubChapterQuizProgress quizProgress
) {
    public LearningProgressStatusResult {
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(quizProgress, "quizProgress must not be null");
    }
}
