package org.firstfolio.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 일일퀘스트 섹션
@Schema(description = "대시보드 daily_quest 섹션")
public final class DailyQuestSummaryResponse {

    @Schema(description = "조회 가능 여부")
    private final boolean available;
    @Schema(description = "available=false일 때의 사유", example = "NOT_IMPLEMENTED", allowableValues = {"NOT_IMPLEMENTED"})
    private final String reason;
    @Schema(description = "퀘스트 상태", example = "IN_PROGRESS", allowableValues = {"ASSIGNED", "IN_PROGRESS", "COMPLETED"})
    private final String status;
    @Schema(description = "답안 제출 완료 문항 수", example = "2")
    private final Integer answeredCount;
    @Schema(description = "전체 문항 수", example = "5")
    private final Integer totalCount;

    public DailyQuestSummaryResponse(
        boolean available,
        String reason,
        String status,
        Integer answeredCount,
        Integer totalCount
    ) {
        this.available = available;
        this.reason = reason;
        this.status = status;
        this.answeredCount = answeredCount;
        this.totalCount = totalCount;
    }

    public static DailyQuestSummaryResponse notImplemented() {
        return new DailyQuestSummaryResponse(false, "NOT_IMPLEMENTED", null, null, null);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public Integer getAnsweredCount() {
        return answeredCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }
}
