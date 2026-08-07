package org.firstfolio.portfolio.service;

import java.math.BigDecimal;

/**
 * 체결 결과 (FUNC-035).
 *
 * <p>{@code requestedAmount}와 {@code amount}가 다를 수 있는 경우는 <b>매수형 매수 한 곳뿐</b>이다
 * (정수 주수 내림). 사용자에게 "500만원 중 483만원이 체결됐다"를 알려야 하므로 둘 다 돌려준다.</p>
 */
public final class TradeResult {

    private final Long portfolioTransactionId;
    private final String transactionType;
    private final Long productId;
    private final BigDecimal requestedAmount;
    private final BigDecimal amount;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final String status;
    private final BigDecimal cashBalance;

    public TradeResult(
            Long portfolioTransactionId,
            String transactionType,
            Long productId,
            BigDecimal requestedAmount,
            BigDecimal amount,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String status,
            BigDecimal cashBalance
    ) {
        this.portfolioTransactionId = portfolioTransactionId;
        this.transactionType = transactionType;
        this.productId = productId;
        this.requestedAmount = requestedAmount;
        this.amount = amount;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.status = status;
        this.cashBalance = cashBalance;
    }

    public Long getPortfolioTransactionId() {
        return portfolioTransactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getStatus() {
        return status;
    }

    /** 체결 후 남은 모의 현금. */
    public BigDecimal getCashBalance() {
        return cashBalance;
    }
}
