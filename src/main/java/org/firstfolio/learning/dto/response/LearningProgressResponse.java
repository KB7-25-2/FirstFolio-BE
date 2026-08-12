package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LearningProgress;

import java.time.LocalDateTime;

@Schema(description = "사용자의 소단원 학습 진행 상태")
public record LearningProgressResponse(
        @Schema(description = "소단원 ID", example = "101") long subChapterId,
        @Schema(description = "실제로 학습하는 강좌 콘텐츠 버전 ID", example = "301")
        long contentVersionId,
        @Schema(description = "마지막으로 학습한 JSON 페이지 ID", example = "page-2",
                nullable = true)
        String lastPageId,
        @Schema(description = "학습 상태", example = "IN_PROGRESS") String status,
        @Schema(description = "최초 학습 시작 시각", nullable = true)
        LocalDateTime startedAt,
        @Schema(description = "최초 학습 완료 시각", nullable = true)
        LocalDateTime completedAt
) {
    public static LearningProgressResponse from(LearningProgress progress) {
        return new LearningProgressResponse(
                progress.getSubChapterId(),
                progress.getContentVersionId(),
                progress.getLastPageId(),
                progress.getStatus().name(),
                progress.getStartedAt(),
                progress.getCompletedAt()
        );
    }
}
