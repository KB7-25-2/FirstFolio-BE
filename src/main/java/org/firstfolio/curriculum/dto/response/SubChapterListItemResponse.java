package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.SubChapter;

public record SubChapterListItemResponse(
        long subChapterId,
        String title,
        int displayOrder,
        Long currentContentVersionId,
        @JsonProperty("is_active") boolean isActive
) {
    public static SubChapterListItemResponse from(SubChapter chapter) {
        return new SubChapterListItemResponse(
                chapter.getSubChapterId(),
                chapter.getTitle(),
                chapter.getDisplayOrder(),
                chapter.getCurrentContentVersionId(),
                chapter.isActive()
        );
    }
}
