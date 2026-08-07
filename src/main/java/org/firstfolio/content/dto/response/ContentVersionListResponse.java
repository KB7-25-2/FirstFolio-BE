package org.firstfolio.content.dto.response;

import org.firstfolio.content.domain.ContentVersionHistory;

import java.util.List;

public record ContentVersionListResponse(
        List<ContentVersionListItemResponse> items
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
