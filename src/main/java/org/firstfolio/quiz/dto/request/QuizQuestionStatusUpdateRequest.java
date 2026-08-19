package org.firstfolio.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "퀴즈 문항 상태 전환 요청 — 현재 DRAFT to REVIEW 전환만 지원")
public record QuizQuestionStatusUpdateRequest(
        @Schema(description = "전환할 상태", example = "REVIEW") String status
) {
}
