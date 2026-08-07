package org.firstfolio.content.dto.response;

import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

import java.time.LocalDateTime;

public record ContentVersionListItemResponse(
        long contentVersionId,
        long subChapterId,
        int versionNo,
        String schemaVersion,
        ContentVersionStatus status,
        LocalDateTime publishedAt,
        long createdBy,
        LocalDateTime createdAt,
        boolean current
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
