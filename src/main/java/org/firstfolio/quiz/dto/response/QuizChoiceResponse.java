package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizChoice;

@Schema(description = "사용자에게 노출하는 퀴즈 선택지")
public record QuizChoiceResponse(
        @Schema(description = "선택지 키", example = "A") String key,
        @Schema(description = "선택지 문구", example = "예금은 약정 조건에 따라 이자를 받을 수 있다.") String label
) {
    public static QuizChoiceResponse from(QuizChoice choice) {
        return new QuizChoiceResponse(choice.key(), choice.label());
    }
}
