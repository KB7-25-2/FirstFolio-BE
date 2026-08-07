package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.SubChapter;

public record SubChapterCreateResponse(
        long subChapterId,
        long mainChapterId,
        String title,
        int displayOrder,
        Long currentContentVersionId,
        @JsonProperty("is_active") boolean isActive
) {
    public static SubChapterCreateResponse from(SubChapter chapter) {
        return new SubChapterCreateResponse(
                chapter.getSubChapterId(),
                chapter.getMainChapterId(),
                chapter.getTitle(),
                chapter.getDisplayOrder(),
                chapter.getCurrentContentVersionId(),
                chapter.isActive()
        );
    }
}
