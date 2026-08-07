package org.firstfolio.content.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

import java.time.LocalDateTime;

@Schema(description = "학습 콘텐츠 버전 이력 한 건")
public record ContentVersionListItemResponse(
        @Schema(description = "콘텐츠 버전 ID", example = "301") long contentVersionId,
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "버전 번호", example = "2") int versionNo,
        @Schema(description = "lesson JSON 스키마 버전", example = "1.0") String schemaVersion,
        @Schema(description = "버전 상태", example = "PUBLISHED") ContentVersionStatus status,
        @Schema(description = "공개 시각. 공개 전이면 null", example = "2026-08-07T11:00:00") LocalDateTime publishedAt,
        @Schema(description = "생성한 관리자 ID", example = "1") long createdBy,
        @Schema(description = "생성 시각", example = "2026-08-07T10:30:00") LocalDateTime createdAt,
        @Schema(description = "현재 공개 버전 여부", example = "true") boolean current
) {
    public static ContentVersionListItemResponse from(
            ContentVersion version,
            Long currentContentVersionId
    ) {
        return new ContentVersionListItemResponse(
                version.getContentVersionId(),
                version.getSubChapterId(),
                version.getVersionNo(),
                version.getSchemaVersion(),
                version.getStatus(),
                version.getPublishedAt(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getContentVersionId().equals(currentContentVersionId)
        );
    }
}
