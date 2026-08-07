package org.firstfolio.content.domain;

import java.util.List;

public record ContentVersionHistory(
        List<ContentVersion> versions,
        Long currentContentVersionId
) {
    public ContentVersionHistory {
        versions = List.copyOf(versions);
    }
}
