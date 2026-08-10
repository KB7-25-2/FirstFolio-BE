package org.firstfolio.learning.domain;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;

public record LearningProgressUpdateCommand(
        long contentVersionId,
        String lastPageId,
        LearningProgressStatus status
) {
    public static LearningProgressUpdateCommand of(
            Long contentVersionId,
            String lastPageId,
            String status
    ) {
        if (contentVersionId == null || contentVersionId <= 0) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "콘텐츠 버전 ID가 필요합니다."
            );
        }

        LearningProgressStatus parsed;
        try {
            parsed = LearningProgressStatus.valueOf(status);
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "학습 상태는 IN_PROGRESS 또는 COMPLETED여야 합니다."
            );
        }

        if (parsed == LearningProgressStatus.NOT_STARTED) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "NOT_STARTED는 진도 저장 상태로 사용할 수 없습니다."
            );
        }

        if (lastPageId != null && lastPageId.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_PAGE_ID);
        }

        return new LearningProgressUpdateCommand(
                contentVersionId,
                lastPageId,
                parsed
        );
    }
}
