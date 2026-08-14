package org.firstfolio.content.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

import java.time.LocalDateTime;

@Schema(description = "학습 콘텐츠 수동 비공개 결과")
public record ContentVersionRetireResponse(
        @Schema(description = "폐기된 콘텐츠 버전 ID", example = "301")
        long contentVersionId,
        @Schema(description = "폐기 후 상태", example = "RETIRED")
        ContentVersionStatus status,
        @Schema(description = "최초 공개 시각", example = "2026-08-07T11:00:00Z")
        LocalDateTime publishedAt,
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "현재 공개 버전 여부", example = "false")
        boolean current
) {

    public static ContentVersionRetireResponse from(ContentVersion version) {
        return new ContentVersionRetireResponse(
                version.getContentVersionId(),
                version.getStatus(),
                version.getPublishedAt(),
                version.getSubChapterId(),
                false
        );
    }
}
