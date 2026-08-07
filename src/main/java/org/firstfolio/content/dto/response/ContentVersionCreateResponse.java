package org.firstfolio.content.dto.response;

import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;

public record ContentVersionCreateResponse(
        long contentVersionId,
        long subChapterId,
        int versionNo,
        String schemaVersion,
        ContentVersionStatus status,
        boolean validated
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
