package org.firstfolio.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장할 레벨 테스트 문항 답안")
public record LevelTestAnswerItemRequest(
        @Schema(description = "응시에 포함된 문항 ID", example = "1001") Long questionId,
        @Schema(description = "저장할 단일 답안") QuizAnswerSelectionRequest answer
) {
    public String selectedKey() {
        return answer == null ? null : answer.key();
    }
}
