package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumSourceType;

@Schema(description = "확정된 개인 커리큘럼 항목")
public record CurriculumConfirmItemResponse(
        @Schema(description = "대단원 ID", example = "1") long mainChapterId,
        @Schema(description = "대단원명", example = "포트폴리오 기초") String title,
        @Schema(description = "커리큘럼 구성 출처", example = "FOUNDATION") CurriculumSourceType sourceType,
        @Schema(description = "학습 표시 순서", example = "1") int displayOrder
) {
    public static CurriculumConfirmItemResponse from(
            CurriculumDraftItem item
    ) {
        return new CurriculumConfirmItemResponse(
                item.mainChapterId(),
                item.title(),
                item.sourceType(),
                item.displayOrder()
        );
    }
}
