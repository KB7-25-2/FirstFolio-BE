package org.firstfolio.quiz.domain;

import org.firstfolio.curriculum.domain.AssetType;

public record LevelTestChapterGradingResult(
        long mainChapterId,
        AssetType assetType,
        int totalCount,
        int correctCount,
        boolean allCorrect
) {
}
