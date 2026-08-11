package org.firstfolio.learning.domain;

import java.util.Objects;

public record LearningContinueResult(
        long curriculumItemId,
        long mainChapterId,
        long subChapterId,
        long contentVersionId,
        String lastPageId,
        int progressPercent,
        String route
) {
    public LearningContinueResult {
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException(
                    "progressPercent must be between 0 and 100"
            );
        }
        Objects.requireNonNull(route, "route must not be null");
    }
}
