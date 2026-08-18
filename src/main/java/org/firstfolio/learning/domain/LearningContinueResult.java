package org.firstfolio.learning.domain;

import java.util.Objects;

public record LearningContinueResult(
        LearningContinueTarget targetType,
        long curriculumItemId,
        long mainChapterId,
        Long subChapterId,
        Long contentVersionId,
        Long attemptId,
        String lastPageId,
        int progressPercent,
        String route
) {
    public LearningContinueResult(
            long curriculumItemId,
            long mainChapterId,
            long subChapterId,
            long contentVersionId,
            String lastPageId,
            int progressPercent,
            String route
    ) {
        this(
                LearningContinueTarget.LESSON,
                curriculumItemId,
                mainChapterId,
                subChapterId,
                contentVersionId,
                null,
                lastPageId,
                progressPercent,
                route
        );
    }

    public LearningContinueResult {
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException(
                    "progressPercent must be between 0 and 100"
            );
        }
        Objects.requireNonNull(route, "route must not be null");
    }
}
