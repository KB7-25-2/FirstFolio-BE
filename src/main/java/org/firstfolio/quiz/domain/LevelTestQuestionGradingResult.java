package org.firstfolio.quiz.domain;

import org.firstfolio.curriculum.domain.AssetType;

public record LevelTestQuestionGradingResult(
        long questionId,
        long mainChapterId,
        AssetType assetType,
        boolean correct
) {
}
