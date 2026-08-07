package org.firstfolio.content.dto.response;

import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

import java.time.LocalDateTime;

public record ContentVersionPublishResponse(
        long contentVersionId,
        ContentVersionStatus status,
        LocalDateTime publishedAt,
        long subChapterId,
        boolean current
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
