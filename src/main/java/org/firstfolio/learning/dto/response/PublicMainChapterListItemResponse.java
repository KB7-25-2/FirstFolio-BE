package org.firstfolio.learning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.MainChapter;

@Schema(description = "사용자에게 공개되는 대단원 목록 항목")
public record PublicMainChapterListItemResponse(
        @Schema(description = "대단원 ID", example = "2") long mainChapterId,
        @Schema(description = "대단원 유형", example = "ASSET") String chapterType,
        @Schema(description = "자산군. FOUNDATION이면 null", example = "DEPOSIT_SAVINGS")
        AssetType assetType,
        @Schema(description = "대단원 제목", example = "예·적금") String title,
        @Schema(description = "대단원 설명", example = "예금과 적금의 기본 원리")
        String description,
        @Schema(description = "표시 순서", example = "2") int displayOrder,
        @JsonProperty("is_required")
        @Schema(description = "필수 과정 여부", example = "false") boolean isRequired
) {
    public static PublicMainChapterListItemResponse from(MainChapter chapter) {
        return new PublicMainChapterListItemResponse(
                chapter.getMainChapterId(),
                chapter.getChapterType().name(),
                chapter.getAssetType(),
                chapter.getTitle(),
                chapter.getDescription(),
                chapter.getDisplayOrder(),
                chapter.isRequired()
        );
    }
}
