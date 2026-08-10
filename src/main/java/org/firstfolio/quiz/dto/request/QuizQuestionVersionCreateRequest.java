package org.firstfolio.quiz.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기존 논리 문항의 새 버전 등록 요청")
public record QuizQuestionVersionCreateRequest(
        @Schema(description = "새 버전 문제 본문") String prompt,
        @Schema(description = "상황판단형 시나리오", nullable = true)
        JsonNode scenarioJson,
        @Schema(description = "새 선택지 배열") JsonNode optionsJson,
        @Schema(description = "새 정답 선택지 키") JsonNode correctAnswerJson,
        @Schema(description = "새 해설") String explanation,
        @Schema(description = "AI 생성 문항의 새 근거 자료", nullable = true)
        JsonNode sourceRefsJson
) {
}
