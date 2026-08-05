package org.firstfolio.portfolio.service;

import java.math.BigDecimal;

/**
 * 초기 모의투자금 지급 결과.
 *
 * <p>API_DOCS {@code POST /quiz-attempts/{attempt_id}/submit} 응답의
 * {@code foundation_grant} 객체에 그대로 대응한다.</p>
 */
public class InitialGrantResult {

    /** 이번 호출에서 실제로 지급했으면 true, 이미 지급돼 있었으면 false. */
    private final boolean granted;

    private final BigDecimal amount;
    private final Long portfolioId;

    public InitialGrantResult(boolean granted, BigDecimal amount, Long portfolioId) {
        this.granted = granted;
        this.amount = amount;
        this.portfolioId = portfolioId;
    }

    public boolean isGranted() {
        return granted;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }
}
