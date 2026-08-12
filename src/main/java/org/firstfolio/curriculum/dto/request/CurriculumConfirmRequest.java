package org.firstfolio.curriculum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "개인 커리큘럼 확정 요청")
public record CurriculumConfirmRequest(
        @Schema(
                description = "확정할 활성 ASSET 대단원 ID. FOUNDATION은 포함하지 않음",
                example = "[3, 2, 4]"
        )
        List<Long> mainChapterIds
) {
}
