package org.firstfolio.portfolio.service;

import java.math.BigDecimal;

/**
 * 체결 결과 (FUNC-035).
 *
 * <p>{@code requestedAmount}와 {@code amount}가 다를 수 있는 경우는 <b>매수형 매수 한 곳뿐</b>이다
 * (정수 주수 내림). 사용자에게 "500만원 중 483만원이 체결됐다"를 알려야 하므로 둘 다 돌려준다.</p>
 *
 * <p>{@code amount}(체결액)와 {@code netCashAmount}(현금 증감)도 다르다 — 수수료·세금 때문이다.
 * <b>사용자가 실제로 낸 돈은 {@code netCashAmount}</b>이므로 화면은 이 값을 보여줘야 한다.
 * 체결액만 보여주면 잔액이 맞지 않는 것으로 보인다.</p>
 */
public final class TradeResult {

    private final Long portfolioTransactionId;
    private final String transactionType;
    private final Long productId;
    private final BigDecimal requestedAmount;
    private final BigDecimal amount;
    private final BigDecimal feeAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal netCashAmount;
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
            BigDecimal feeAmount,
            BigDecimal taxAmount,
            BigDecimal netCashAmount,
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
        this.feeAmount = feeAmount;
        this.taxAmount = taxAmount;
        this.netCashAmount = netCashAmount;
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

    /** 매매 수수료. 예·적금·채권 거래에서는 {@code 0.00}이다. */
    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    /** 증권거래세. <b>매수와 예·적금·채권 거래에서는 {@code 0.00}</b>이다. */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /** 실제로 오간 현금. 매수는 체결액보다 크고 매도는 작다. */
    public BigDecimal getNetCashAmount() {
        return netCashAmount;
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
