package org.firstfolio.learning.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LearningProgressUpdateCommand;

@Schema(description = "소단원 학습 진도 저장 정보")
public record LearningProgressUpdateRequest(
        @Schema(description = "실제로 학습 중인 콘텐츠 버전 ID", example = "301")
        Long contentVersionId,
        @Schema(description = "JSON 내부 마지막 학습 페이지 ID", example = "page-2",
                nullable = true)
        String lastPageId,
        @Schema(description = "저장할 학습 상태", allowableValues = {
                "IN_PROGRESS", "COMPLETED"
        }, example = "IN_PROGRESS")
        String status
) {
    public LearningProgressUpdateCommand toCommand() {
        return LearningProgressUpdateCommand.of(
                contentVersionId,
                lastPageId,
                status
        );
    }
}
