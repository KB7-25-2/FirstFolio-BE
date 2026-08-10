package org.firstfolio.content.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

@Schema(description = "학습 콘텐츠 버전 생성 결과")
public record ContentVersionCreateResponse(
        @Schema(description = "콘텐츠 버전 ID", example = "301") long contentVersionId,
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "버전 번호", example = "2") int versionNo,
        @Schema(description = "lesson JSON 스키마 버전", example = "1.0") String schemaVersion,
        @Schema(description = "버전 상태", example = "DRAFT") ContentVersionStatus status,
        @Schema(description = "스키마 검증 통과 여부", example = "true") boolean validated
) {

    public static ContentVersionCreateResponse from(ContentVersion version) {
        return new ContentVersionCreateResponse(
                version.getContentVersionId(),
                version.getSubChapterId(),
                version.getVersionNo(),
                version.getSchemaVersion(),
                version.getStatus(),
                true
        );
    }
}
