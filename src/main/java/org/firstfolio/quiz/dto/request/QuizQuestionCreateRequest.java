package org.firstfolio.quiz.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자용 신규 퀴즈 문항 등록 요청")
public record QuizQuestionCreateRequest(
        @Schema(description = "문항 버전의 논리 키", example = "deposit-basic-001")
        String questionKey,
        @Schema(description = "문항 사용처", example = "SUB_CHAPTER")
        String usageType,
        @Schema(description = "대단원 ID", nullable = true, example = "2")
        Long mainChapterId,
        @Schema(description = "소단원 ID", nullable = true, example = "101")
        Long subChapterId,
        @Schema(description = "문항 유형", example = "SINGLE_CHOICE")
        String questionType,
        @Schema(description = "난이도", nullable = true, example = "EASY")
        String difficulty,
        @Schema(description = "문제 본문")
        String prompt,
        @Schema(description = "상황판단형 시나리오", nullable = true)
        JsonNode scenarioJson,
        @Schema(description = "선택지 배열")
        JsonNode optionsJson,
        @Schema(description = "정답 선택지 키")
        JsonNode correctAnswerJson,
        @Schema(description = "채점 후 표시할 해설")
        String explanation
) {
}
