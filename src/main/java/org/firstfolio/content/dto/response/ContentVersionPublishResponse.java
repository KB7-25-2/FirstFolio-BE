package org.firstfolio.content.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

import java.time.LocalDateTime;

@Schema(description = "학습 콘텐츠 공개 결과")
public record ContentVersionPublishResponse(
        @Schema(description = "공개된 콘텐츠 버전 ID", example = "301") long contentVersionId,
        @Schema(description = "공개 후 상태", example = "PUBLISHED") ContentVersionStatus status,
        @Schema(description = "공개 시각", example = "2026-08-07T11:00:00") LocalDateTime publishedAt,
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "현재 공개 버전 여부", example = "true") boolean current
) {
    public static ContentVersionPublishResponse from(ContentVersion version) {
        return new ContentVersionPublishResponse(
                version.getContentVersionId(),
                version.getStatus(),
                version.getPublishedAt(),
                version.getSubChapterId(),
                true
        );
    }
}
