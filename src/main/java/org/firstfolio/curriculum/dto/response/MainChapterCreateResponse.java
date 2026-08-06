package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.MainChapter;

public record MainChapterCreateResponse(
        long mainChapterId,
        String chapterType,
        String assetType,
        String title,
        @JsonProperty("is_active") boolean isActive
) {
    public static MainChapterCreateResponse from(MainChapter chapter) {
        return new MainChapterCreateResponse(
                chapter.getMainChapterId(),
                chapter.getChapterType().name(),
                chapter.getAssetType() == null ? null : chapter.getAssetType().name(),
                chapter.getTitle(),
                chapter.isActive()
        );
    }
}
