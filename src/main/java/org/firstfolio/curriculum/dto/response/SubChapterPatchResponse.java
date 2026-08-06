package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.SubChapter;

public record SubChapterPatchResponse(
        long subChapterId,
        String title,
        int displayOrder,
        @JsonProperty("is_active") boolean isActive
) {
    public static SubChapterPatchResponse from(SubChapter chapter) {
        return new SubChapterPatchResponse(
                chapter.getSubChapterId(),
                chapter.getTitle(),
                chapter.getDisplayOrder(),
                chapter.isActive()
        );
    }
}
