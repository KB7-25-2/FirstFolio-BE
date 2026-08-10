package org.firstfolio.content.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.content.domain.ContentVersionHistory;

import java.util.List;

@Schema(description = "소단원의 콘텐츠 버전 이력")
public record ContentVersionListResponse(
        @Schema(description = "최신 버전부터 정렬된 버전 목록") List<ContentVersionListItemResponse> items
) {
    public static ContentVersionListResponse from(ContentVersionHistory history) {
        return new ContentVersionListResponse(
                history.versions().stream()
                        .map(version -> ContentVersionListItemResponse.from(
                                version,
                                history.currentContentVersionId()
                        ))
                        .toList()
        );
    }
}
