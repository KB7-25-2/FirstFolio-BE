package org.firstfolio.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 학습 진행상황 요약
@Schema(description = "대시보드 learning 섹션")
public final class LearningSummaryResponse {

    @Schema(description = "이어갈 학습 위치 존재 여부")
    private final boolean available;
    @Schema(description = "available=false일 때의 사유", example = "NOT_STARTED", allowableValues = {"NOT_STARTED"})
    private final String reason;
    @Schema(description = "대단원 ID", example = "3")
    private final Long mainChapterId;
    @Schema(description = "소단원 ID", example = "12")
    private final Long subChapterId;
    @Schema(description = "현재 소단원 내 읽기 진행률(%)", example = "50")
    private final Integer progressPercent;

    public LearningSummaryResponse(
        boolean available,
        String reason,
        Long mainChapterId,
        Long subChapterId,
        Integer progressPercent
    ) {
        this.available = available;
        this.reason = reason;
        this.mainChapterId = mainChapterId;
        this.subChapterId = subChapterId;
        this.progressPercent = progressPercent;
    }

    public static LearningSummaryResponse unavailable(String reason) {
        return new LearningSummaryResponse(false, reason, null, null, null);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getReason() {
        return reason;
    }

    public Long getMainChapterId() {
        return mainChapterId;
    }

    public Long getSubChapterId() {
        return subChapterId;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }
}
