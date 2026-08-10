package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.PublicSubChapter;

@Schema(description = "사용자에게 공개되는 소단원 목록 항목")
public record PublicSubChapterListItemResponse(
        @Schema(description = "소단원 ID", example = "101") long subChapterId,
        @Schema(description = "소단원 제목", example = "예금의 이해") String title,
        @Schema(description = "소단원 설명", example = "예금의 기본 개념") String description,
        @Schema(description = "표시 순서", example = "1") int displayOrder,
        @Schema(description = "현재 공개 강좌 콘텐츠 존재 여부", example = "true")
        boolean contentAvailable
) {
    public static PublicSubChapterListItemResponse from(PublicSubChapter chapter) {
        return new PublicSubChapterListItemResponse(
                chapter.getSubChapterId(),
                chapter.getTitle(),
                chapter.getDescription(),
                chapter.getDisplayOrder(),
                chapter.isContentAvailable()
        );
    }
}
