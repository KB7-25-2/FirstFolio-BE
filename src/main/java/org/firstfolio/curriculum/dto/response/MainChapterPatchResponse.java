package org.firstfolio.curriculum.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.MainChapter;

import java.time.LocalDateTime;

@Schema(description = "대단원 수정 결과")
public record MainChapterPatchResponse(
        @Schema(description = "대단원 ID", example = "1") long mainChapterId,
        @Schema(description = "수정 후 제목", example = "예·적금 기초") String title,
        @Schema(description = "수정 후 노출 순서", example = "2") int displayOrder,
        @JsonProperty("is_active") @Schema(description = "수정 후 활성 여부", example = "true") boolean isActive,
        @Schema(description = "수정 시각", example = "2026-08-07T10:15:00") LocalDateTime updatedAt
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
