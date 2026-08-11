package org.firstfolio.quiz.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;

import java.util.List;

@Schema(description = "응시에 고정된 퀴즈 문항. 정답과 해설은 포함하지 않습니다.")
public record QuizAttemptQuestionResponse(
        @Schema(description = "출제된 문항 버전 ID", example = "1001") long questionId,
        @Schema(description = "응시 내 표시 순서", example = "1") int displayOrder,
        @Schema(description = "문항 유형", example = "SINGLE_CHOICE") QuizQuestionType questionType,
        @Schema(description = "문항 생성 방식", example = "HUMAN") QuizGenerationType generationType,
        @Schema(description = "문제 본문", example = "예금에 대한 설명으로 올바른 것은?") String prompt,
        @Schema(description = "시나리오 문항 정보. 일반 문항은 null", nullable = true) JsonNode scenario,
        @Schema(description = "선택지 목록") List<QuizChoiceResponse> choices
) {
    public QuizAttemptQuestionResponse {
        scenario = scenario == null ? null : scenario.deepCopy();
        choices = List.copyOf(choices);
    }

    public static QuizAttemptQuestionResponse from(QuizAttemptQuestion question) {
        return new QuizAttemptQuestionResponse(
                question.questionId(),
                question.displayOrder(),
                question.questionType(),
                question.generationType(),
                question.prompt(),
                question.scenario(),
                question.choices().stream().map(QuizChoiceResponse::from).toList()
        );
    }

    @Override
    public JsonNode scenario() {
        return scenario == null ? null : scenario.deepCopy();
    }
}
