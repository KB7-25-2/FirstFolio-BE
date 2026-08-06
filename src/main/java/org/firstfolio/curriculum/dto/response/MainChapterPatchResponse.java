package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.curriculum.domain.MainChapter;

import java.time.LocalDateTime;

public record MainChapterPatchResponse(
        long mainChapterId,
        String title,
        int displayOrder,
        @JsonProperty("is_active") boolean isActive,
        LocalDateTime updatedAt
) {
    public static MainChapterPatchResponse from(MainChapter chapter) {
        return new MainChapterPatchResponse(
                chapter.getMainChapterId(),
                chapter.getTitle(),
                chapter.getDisplayOrder(),
                chapter.isActive(),
                chapter.getUpdatedAt()
        );
    }
}
