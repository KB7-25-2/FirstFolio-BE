package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.SubChapter;

@Schema(description = "소단원 목록 항목")
public record SubChapterListItemResponse(
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "소단원 제목", example = "예금과 적금의 차이") String title,
        @Schema(description = "노출 순서", example = "1") int displayOrder,
        @Schema(description = "현재 공개 콘텐츠 버전 ID. 공개 전이면 null", example = "301") Long currentContentVersionId,
        @JsonProperty("is_active") @Schema(description = "활성 여부", example = "true") boolean isActive
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
