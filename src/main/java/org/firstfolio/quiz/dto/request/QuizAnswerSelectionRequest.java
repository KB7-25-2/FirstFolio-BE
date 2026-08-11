package org.firstfolio.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자가 선택한 단일 답안")
public record QuizAnswerSelectionRequest(
        @Schema(description = "선택지 키", example = "B") String key
) {
}
