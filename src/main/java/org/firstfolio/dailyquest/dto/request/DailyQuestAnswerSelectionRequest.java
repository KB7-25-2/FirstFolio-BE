package org.firstfolio.dailyquest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일일 퀘스트에서 선택한 단일 답안")
public record DailyQuestAnswerSelectionRequest(
        @Schema(description = "선택지 키", example = "A") String key
) {
}
