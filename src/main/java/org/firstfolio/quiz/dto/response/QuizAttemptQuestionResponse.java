package org.firstfolio.quiz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;

import java.util.List;

@Schema(description = "응시에 고정된 퀴즈 문항. 제출한 문항에만 기존 채점 결과를 포함합니다.")
public record QuizAttemptQuestionResponse(
        @Schema(description = "출제된 문항 버전 ID", example = "1001") long questionId,
        @Schema(description = "응시 내 표시 순서", example = "1") int displayOrder,
        @Schema(description = "문항 유형", example = "SINGLE_CHOICE") QuizQuestionType questionType,
        @Schema(description = "문항 생성 방식", example = "HUMAN") QuizGenerationType generationType,
        @Schema(description = "문제 본문", example = "예금에 대한 설명으로 올바른 것은?") String prompt,
        @Schema(description = "시나리오 문항 정보. 일반 문항은 null", nullable = true) JsonNode scenario,
        @Schema(description = "선택지 목록") List<QuizChoiceResponse> choices,
        @Schema(description = "이 응시에서 답변을 제출한 문항인지 여부",
                example = "true")
        boolean answered,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "제출한 선택지 키. 미답변이면 null", example = "A",
                nullable = true)
        String selectedKey,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "정답 여부. 미답변이면 null", example = "true",
                nullable = true)
        Boolean isCorrect,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "정답. 미답변이면 null",
                nullable = true)
        QuizCorrectAnswerResponse correctAnswer,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "채점 해설. 미답변이면 null", nullable = true)
        String explanation
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
                question.choices().stream().map(QuizChoiceResponse::from).toList(),
                question.answered(),
                question.selectedKey(),
                question.correct(),
                question.correctKey() == null
                        ? null
                        : new QuizCorrectAnswerResponse(question.correctKey()),
                question.explanation()
        );
    }

    @Override
    public JsonNode scenario() {
        return scenario == null ? null : scenario.deepCopy();
    }
}
