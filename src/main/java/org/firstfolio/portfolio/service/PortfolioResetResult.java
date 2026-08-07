package org.firstfolio.portfolio.service;

import java.math.BigDecimal;

/**
 * 포트폴리오 초기화 결과 (FUNC-037).
 *
 * <p>닫힌 세대와 새 세대를 함께 돌려준다. 클라이언트가 "무엇이 끝나고 무엇이 시작됐는지"를
 * 한 번에 알 수 있어야 화면 전환과 안내 문구를 만들 수 있다.</p>
 */
public final class PortfolioResetResult {

    private final Long closedPortfolioId;
    private final Long newPortfolioId;
    private final Integer generationNo;
    private final BigDecimal cashBalance;
    private final Long resetTransactionId;

    public PortfolioResetResult(
            Long closedPortfolioId,
            Long newPortfolioId,
            Integer generationNo,
            BigDecimal cashBalance,
            Long resetTransactionId
    ) {
        this.closedPortfolioId = closedPortfolioId;
        this.newPortfolioId = newPortfolioId;
        this.generationNo = generationNo;
        this.cashBalance = cashBalance;
        this.resetTransactionId = resetTransactionId;
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
