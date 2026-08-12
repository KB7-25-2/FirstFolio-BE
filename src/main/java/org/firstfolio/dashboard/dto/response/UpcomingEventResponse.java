package org.firstfolio.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 대시보드 upcoming_events 섹션 한 건.
 *
 * <p>FE {@code dashboardService.js}가 이 섹션을 배열 그대로 기대하고 바로 {@code .map()}을
 * 호출하기 때문에, {@code available}/{@code reason} 래핑 없이 JSON 최상위에 배열로 나간다
 * (포트폴리오가 없으면 빈 배열).</p>
 */
@Schema(description = "예정 자산 이벤트 한 건")
public final class UpcomingEventResponse {

    @Schema(description = "이벤트 유형", example = "MATURITY", allowableValues = {"INTEREST", "DIVIDEND", "MATURITY"})
    private final String type;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "예정 일시", example = "2026-09-01T00:00:00")
    private final LocalDateTime scheduledAt;

    public UpcomingEventResponse(String type, LocalDateTime scheduledAt) {
        this.type = type;
        this.scheduledAt = scheduledAt;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }
}
