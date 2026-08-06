package org.firstfolio.portfolio.dto.response;

import org.firstfolio.portfolio.service.PortfolioResetResult;

import java.math.BigDecimal;

/**
 * 포트폴리오 초기화 결과 (API_DOCS {@code POST /portfolios/current/reset}).
 *
 * <p>닫힌 세대와 새 세대를 함께 준다. 화면이 "무엇이 끝나고 무엇이 시작됐는지"를 알아야
 * 전환과 안내를 만들 수 있다.</p>
 */
public class PortfolioResetResponse {

    private final Long closedPortfolioId;
    private final Long newPortfolioId;
    private final Integer generationNo;
    private final BigDecimal cashBalance;
    private final Long resetTransactionId;

    public PortfolioResetResponse(PortfolioResetResult result) {
        this.closedPortfolioId = result.getClosedPortfolioId();
        this.newPortfolioId = result.getNewPortfolioId();
        this.generationNo = result.getGenerationNo();
        this.cashBalance = result.getCashBalance();
        this.resetTransactionId = result.getResetTransactionId();
    }

    public Long getClosedPortfolioId() {
        return closedPortfolioId;
    }

    public Long getNewPortfolioId() {
        return newPortfolioId;
    }

    public Integer getGenerationNo() {
        return generationNo;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public Long getResetTransactionId() {
        return resetTransactionId;
    }
}
