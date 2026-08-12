package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;

import java.util.List;

@Schema(description = "검증하고 정규화한 개인 커리큘럼 초안")
public record CurriculumDraftEditResponse(
        @Schema(description = "FOUNDATION을 포함한 정규화된 초안 항목") List<CurriculumDraftItemResponse> items
) {
    public CurriculumDraftEditResponse {
        items = List.copyOf(items);
    }

    public static CurriculumDraftEditResponse from(
            List<CurriculumDraftItem> items
    ) {
        return new CurriculumDraftEditResponse(
                items.stream().map(CurriculumDraftItemResponse::from).toList()
        );
    }
}
