package org.firstfolio.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "퀴즈 문항 답안 제출 요청")
public record QuizAnswerSubmitRequest(
        @Schema(description = "제출할 단일 답안") QuizAnswerSelectionRequest answer
) {
    public String selectedKey() {
        return answer == null ? null : answer.key();
    }
}
