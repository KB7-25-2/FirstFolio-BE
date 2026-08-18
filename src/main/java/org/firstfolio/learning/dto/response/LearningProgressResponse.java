package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressStatusResult;
import org.firstfolio.learning.domain.SubChapterQuizProgress;

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
        LocalDateTime completedAt,
        @Schema(description = "소단원 퀴즈 완료 및 현재 이어풀기 상태")
        QuizProgressResponse quiz
) {
    public static LearningProgressResponse from(
            LearningProgressStatusResult result
    ) {
        LearningProgress progress = result.progress();
        return new LearningProgressResponse(
                progress.getSubChapterId(),
                progress.getContentVersionId(),
                progress.getLastPageId(),
                progress.getStatus().name(),
                progress.getStartedAt(),
                progress.getCompletedAt(),
                QuizProgressResponse.from(result.quizProgress())
        );
    }

    @Schema(description = "소단원 퀴즈 진행 요약")
    public record QuizProgressResponse(
            @Schema(description = "과거 응시를 한 번 이상 완료했는지 여부",
                    example = "false")
            boolean completed,
            @Schema(description = "현재 진행 중인 응시 ID", example = "3001",
                    nullable = true)
            Long activeAttemptId,
            @Schema(description = "현재 응시에서 답변한 문항 수", example = "1")
            int answeredCount,
            @Schema(description = "현재 응시의 전체 문항 수", example = "3")
            int totalCount
    ) {
        private static QuizProgressResponse from(
                SubChapterQuizProgress progress
        ) {
            return new QuizProgressResponse(
                    progress.isCompleted(),
                    progress.getActiveAttemptId(),
                    progress.getAnsweredCount(),
                    progress.getTotalCount()
            );
        }
    }
}
