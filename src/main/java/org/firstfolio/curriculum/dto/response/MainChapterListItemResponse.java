package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.MainChapter;

public record MainChapterListItemResponse(
        long mainChapterId,
        String chapterType,
        String assetType,
        String title,
        int displayOrder,
        @JsonProperty("is_required") boolean isRequired,
        @JsonProperty("is_active") boolean isActive
) {
    public static MainChapterListItemResponse from(MainChapter chapter) {
        return new MainChapterListItemResponse(
                chapter.getMainChapterId(),
                chapter.getChapterType().name(),
                chapter.getAssetType() == null ? null : chapter.getAssetType().name(),
                chapter.getTitle(),
                chapter.getDisplayOrder(),
                chapter.isRequired(),
                chapter.isActive()
        );
    }
}
