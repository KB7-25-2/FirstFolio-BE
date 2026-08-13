package org.firstfolio.dailyquest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.dailyquest.domain.DailyQuestAnswerSaveResult;

@Schema(description = "일일 퀘스트 문항 답안 임시 저장 결과")
public record DailyQuestAnswerSaveResponse(
        @Schema(description = "일일 퀘스트 ID", example = "4001") long dailyQuestId,
        @Schema(description = "일일 퀘스트 문항 배정 ID", example = "5001") long dailyQuestItemId,
        @Schema(description = "저장된 답안") SavedAnswerResponse savedAnswer,
        @Schema(description = "답안을 저장한 문항 수", example = "2") int answeredCount,
        @Schema(description = "전체 문항 수", example = "5") int totalCount
) {
    public static DailyQuestAnswerSaveResponse from(
            DailyQuestAnswerSaveResult result
    ) {
        return new DailyQuestAnswerSaveResponse(
                result.dailyQuestId(),
                result.dailyQuestItemId(),
                new SavedAnswerResponse(result.savedAnswerKey()),
                result.answeredCount(),
                result.totalCount()
        );
    }

    @Schema(description = "임시 저장된 단일 답안")
    public record SavedAnswerResponse(
            @Schema(description = "선택지 키", example = "A") String key
    ) {
    }
}
