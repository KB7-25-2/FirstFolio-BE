package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.SubChapter;

@Schema(description = "소단원 수정 결과")
public record SubChapterPatchResponse(
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "수정 후 제목", example = "예금과 적금 비교") String title,
        @Schema(description = "수정 후 노출 순서", example = "2") int displayOrder,
        @JsonProperty("is_active") @Schema(description = "수정 후 활성 여부", example = "true") boolean isActive
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
