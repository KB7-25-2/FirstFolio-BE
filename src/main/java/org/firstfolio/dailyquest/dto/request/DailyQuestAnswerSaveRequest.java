package org.firstfolio.dailyquest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일일 퀘스트 문항 답안 임시 저장 요청")
public record DailyQuestAnswerSaveRequest(
        @Schema(description = "저장할 단일 답안") DailyQuestAnswerSelectionRequest answer
) {
    public String selectedKey() {
        return answer == null ? null : answer.key();
    }
}
