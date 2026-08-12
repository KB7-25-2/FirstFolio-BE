package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;

import java.util.List;

@Schema(description = "확정된 개인 커리큘럼")
public record CurriculumConfirmResponse(
        @Schema(description = "FOUNDATION을 포함한 확정 커리큘럼 항목") List<CurriculumConfirmItemResponse> items
) {
    public CurriculumConfirmResponse {
        items = List.copyOf(items);
    }

    public static CurriculumConfirmResponse from(
            List<CurriculumDraftItem> items
    ) {
        return new CurriculumConfirmResponse(
                items.stream().map(CurriculumConfirmItemResponse::from).toList()
        );
    }
}
