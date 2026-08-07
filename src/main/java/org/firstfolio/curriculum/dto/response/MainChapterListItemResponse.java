package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.MainChapter;

@Schema(description = "대단원 목록 항목")
public record MainChapterListItemResponse(
        @Schema(description = "대단원 ID", example = "1") long mainChapterId,
        @Schema(description = "대단원 유형", example = "ASSET") String chapterType,
        @Schema(description = "자산군", example = "DEPOSIT") String assetType,
        @Schema(description = "대단원 제목", example = "예·적금") String title,
        @Schema(description = "노출 순서", example = "1") int displayOrder,
        @JsonProperty("is_required") @Schema(description = "필수 과정 여부", example = "false") boolean isRequired,
        @JsonProperty("is_active") @Schema(description = "활성 여부", example = "true") boolean isActive
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
