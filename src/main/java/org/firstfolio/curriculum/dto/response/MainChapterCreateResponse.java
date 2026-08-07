package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.MainChapter;

@Schema(description = "대단원 생성 결과")
public record MainChapterCreateResponse(
        @Schema(description = "대단원 ID", example = "1") long mainChapterId,
        @Schema(description = "대단원 유형", example = "ASSET") String chapterType,
        @Schema(description = "자산군. 필수 과정이면 null 가능", example = "DEPOSIT") String assetType,
        @Schema(description = "대단원 제목", example = "예·적금") String title,
        @JsonProperty("is_active") @Schema(description = "활성 여부", example = "true") boolean isActive
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
