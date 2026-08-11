package org.firstfolio.quiz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizAttemptStatus;

@Schema(description = "문항 채점 후 응시 진행 상태")
public record QuizAnswerAttemptResponse(
        @Schema(description = "응시 상태", example = "IN_PROGRESS") QuizAttemptStatus status,
        @Schema(description = "답변한 문항 수", example = "1") int answeredCount,
        @Schema(description = "전체 문항 수", example = "3") int totalCount,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "완료 시 최종 정답 수", example = "2", nullable = true)
        Integer correctCount,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "완료 시 100점 환산 점수", example = "67", nullable = true)
        Integer score,
        @Schema(description = "응시 완료 여부", example = "false") boolean completed
) {
}
