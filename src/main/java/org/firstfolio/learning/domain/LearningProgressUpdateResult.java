package org.firstfolio.learning.domain;

public record LearningProgressUpdateResult(
        LearningProgress progress,
        boolean updated
) {
}
