package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자용 퀴즈 문항 커서 페이지")
public record QuizQuestionPageResponse(
        @Schema(description = "퀴즈 문항 목록") List<QuizQuestionListItemResponse> items,
        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "1001")
        String nextCursor
) {
}
