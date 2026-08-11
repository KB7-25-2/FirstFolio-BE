package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizChoice;

@Schema(description = "사용자에게 노출하는 퀴즈 선택지")
public record QuizChoiceResponse(
        @Schema(description = "선택지 식별자", example = "A") String id,
        @Schema(description = "선택지 문구", example = "예금은 약정 조건에 따라 이자를 받을 수 있다.") String text
) {
    public static QuizChoiceResponse from(QuizChoice choice) {
        return new QuizChoiceResponse(choice.id(), choice.text());
    }
}
