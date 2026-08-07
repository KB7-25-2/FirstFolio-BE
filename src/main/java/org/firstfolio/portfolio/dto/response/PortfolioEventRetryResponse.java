package org.firstfolio.portfolio.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.portfolio.service.PortfolioEventResult;

import java.time.LocalDateTime;

/**
 * 자산 이벤트 재처리 결과 (API_DOCS {@code POST /internal/portfolio-events/{event_key}/retry}).
 *
 * <p>{@code status}는 <b>재처리 이후</b>의 상태다. 다시 실패하면 {@code FAILED}가 그대로 실려 나가고
 * {@code processed_at}은 비어 있다 — 재처리 실패는 요청 자체의 오류가 아니다.</p>
 */
@Schema(description = "실패한 자산 이벤트 재처리 결과. 재실패도 HTTP 200과 FAILED 상태로 응답")
public class PortfolioEventRetryResponse {

    @Schema(description = "이벤트 멱등 키", example = "interest-8101-8201-20260729T0300Z")
    private final String eventKey;
    @Schema(description = "재처리 이후 상태", example = "COMPLETED", allowableValues = {"COMPLETED", "FAILED"})
    private final String status;
    @Schema(description = "연결된 포트폴리오 거래 ID", example = "8201")
    private final Long portfolioTransactionId;

    /** 아직 반영되지 않았으면 null. */
    @Schema(description = "반영 시각. 다시 실패하면 null", example = "2026-08-07T04:10:00")
    private final LocalDateTime processedAt;

    public PortfolioEventRetryResponse(PortfolioEventResult result) {
        this.eventKey = result.getEventKey();
        this.status = result.getStatus();
        this.portfolioTransactionId = result.getPortfolioTransactionId();
        this.processedAt = result.getProcessedAt();
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getStatus() {
        return status;
    }

    public Long getPortfolioTransactionId() {
        return portfolioTransactionId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
