package org.firstfolio.learning.domain;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.portfolio.service.InitialGrantResult;

import java.util.Objects;

public record MainChapterCompletionResult(
        ChapterType chapterType,
        boolean completedNow,
        InitialGrantResult foundationGrant
) {
    public MainChapterCompletionResult {
        Objects.requireNonNull(chapterType, "chapterType must not be null");
        if (chapterType == ChapterType.FOUNDATION
                && foundationGrant == null) {
            throw new IllegalArgumentException(
                    "foundation completion must include grant result"
            );
        }
        if (chapterType != ChapterType.FOUNDATION
                && foundationGrant != null) {
            throw new IllegalArgumentException(
                    "asset completion must not include foundation grant"
            );
        }
    }
}
