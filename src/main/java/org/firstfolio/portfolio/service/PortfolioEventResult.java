package org.firstfolio.portfolio.service;

import java.time.LocalDateTime;

/** 자산 이벤트 한 건의 현재 상태 (FUNC-041 재처리 응답). */
public final class PortfolioEventResult {

    private final String eventKey;
    private final String status;
    private final Long portfolioTransactionId;
    private final LocalDateTime processedAt;

    public PortfolioEventResult(
            String eventKey,
            String status,
            Long portfolioTransactionId,
            LocalDateTime processedAt
    ) {
        this.eventKey = eventKey;
        this.status = status;
        this.portfolioTransactionId = portfolioTransactionId;
        this.processedAt = processedAt;
    }

    public String getEventKey() {
        return eventKey;
    }

    /**
     * 재처리 <b>이후</b>의 상태.
     *
     * <p>다시 실패하면 {@code FAILED}가 그대로 실려 나간다. 재처리 실패는 요청 자체의 오류가
     * 아니므로 오류 응답으로 만들지 않는다 — 명세의 오류 코드도 404·409 둘뿐이다.</p>
     */
    public String getStatus() {
        return status;
    }

    public Long getPortfolioTransactionId() {
        return portfolioTransactionId;
    }

    /** 아직 반영되지 않았으면 null. */
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
