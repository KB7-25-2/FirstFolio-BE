package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;

import java.util.List;

@Schema(description = "수정된 개인 커리큘럼")
public record CurriculumUpdateResponse(
        @Schema(description = "FOUNDATION을 포함한 수정 커리큘럼 항목")
        List<CurriculumConfirmItemResponse> items
) {
    public CurriculumUpdateResponse {
        items = List.copyOf(items);
    }

    public static CurriculumUpdateResponse from(
            List<CurriculumDraftItem> items
    ) {
        return new CurriculumUpdateResponse(
                items.stream().map(CurriculumConfirmItemResponse::from).toList()
        );
    }
}
