package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumOverviewItem;

import java.time.LocalDateTime;

@Schema(description = "확정된 개인 커리큘럼 항목과 학습 진행 상태")
public record CurriculumOverviewItemResponse(
        @Schema(description = "개인 커리큘럼 항목 ID", example = "501") long curriculumItemId,
        @Schema(description = "대단원 ID", example = "1") long mainChapterId,
        @Schema(description = "대단원명", example = "포트폴리오 기초") String title,
        @Schema(description = "대단원 유형", example = "FOUNDATION") ChapterType chapterType,
        @Schema(description = "학습 표시 순서", example = "1") int displayOrder,
        @Schema(description = "커리큘럼 항목 상태", example = "ACTIVE") CurriculumItemStatus status,
        @Schema(description = "대단원 최초 완료 일시") LocalDateTime completedAt,
        @Schema(description = "활성 소단원 완료율", example = "40") int progressPercent
) {
    public static CurriculumOverviewItemResponse from(
            CurriculumOverviewItem item
    ) {
        return new CurriculumOverviewItemResponse(
                item.curriculumItemId(),
                item.mainChapterId(),
                item.title(),
                item.chapterType(),
                item.displayOrder(),
                item.status(),
                item.completedAt(),
                item.progressPercent()
        );
    }
}
