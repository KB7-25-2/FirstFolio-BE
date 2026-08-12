package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumOverviewItem;

import java.util.List;

@Schema(description = "확정된 개인 커리큘럼과 학습 진행 상태")
public record CurriculumOverviewResponse(
        List<CurriculumOverviewItemResponse> items
) {
    public CurriculumOverviewResponse {
        items = List.copyOf(items);
    }

    public static CurriculumOverviewResponse from(
            List<CurriculumOverviewItem> items
    ) {
        return new CurriculumOverviewResponse(
                items.stream().map(CurriculumOverviewItemResponse::from).toList()
        );
    }
}
