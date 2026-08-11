package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정답 선택지")
public record QuizCorrectAnswerResponse(
        @Schema(description = "정답 선택지 키", example = "C") String key
) {
}
