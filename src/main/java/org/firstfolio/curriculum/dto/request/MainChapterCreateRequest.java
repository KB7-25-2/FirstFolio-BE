package org.firstfolio.curriculum.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;

public record MainChapterCreateRequest(
        ChapterType chapterType,
        AssetType assetType,
        String title,
        String description,
        Integer displayOrder,
        @JsonProperty("is_required") Boolean isRequired
) {
}
