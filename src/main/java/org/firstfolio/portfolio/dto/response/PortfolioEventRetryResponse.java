package org.firstfolio.portfolio.dto.response;

import org.firstfolio.portfolio.service.PortfolioEventResult;

import java.time.LocalDateTime;

/**
 * 자산 이벤트 재처리 결과 (API_DOCS {@code POST /internal/portfolio-events/{event_key}/retry}).
 *
 * <p>{@code status}는 <b>재처리 이후</b>의 상태다. 다시 실패하면 {@code FAILED}가 그대로 실려 나가고
 * {@code processed_at}은 비어 있다 — 재처리 실패는 요청 자체의 오류가 아니다.</p>
 */
public class PortfolioEventRetryResponse {

    private final String eventKey;
    private final String status;
    private final Long portfolioTransactionId;

    /** 아직 반영되지 않았으면 null. */
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
