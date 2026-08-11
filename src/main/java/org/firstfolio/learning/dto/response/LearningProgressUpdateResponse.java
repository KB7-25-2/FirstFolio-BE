package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressUpdateResult;

import java.time.LocalDateTime;

@Schema(description = "소단원 학습 진도 저장 결과")
public record LearningProgressUpdateResponse(
        @Schema(description = "소단원 ID", example = "101") long subChapterId,
        @Schema(description = "실제로 학습 중인 콘텐츠 버전 ID", example = "301")
        long contentVersionId,
        @Schema(description = "마지막으로 학습한 JSON 페이지 ID", example = "page-2",
                nullable = true)
        String lastPageId,
        @Schema(description = "학습 상태", example = "IN_PROGRESS") String status,
        @Schema(description = "최초 학습 시작 시각") LocalDateTime startedAt,
        @Schema(description = "최초 학습 완료 시각", nullable = true)
        LocalDateTime completedAt,
        @Schema(description = "이번 요청에서 진도가 생성 또는 변경됐는지 여부",
                example = "true")
        boolean updated
) {
    public static LearningProgressUpdateResponse from(
            LearningProgressUpdateResult result
    ) {
        LearningProgress progress = result.progress();
        return new LearningProgressUpdateResponse(
                progress.getSubChapterId(),
                progress.getContentVersionId(),
                progress.getLastPageId(),
                progress.getStatus().name(),
                progress.getStartedAt(),
                progress.getCompletedAt(),
                result.updated()
        );
    }
}
