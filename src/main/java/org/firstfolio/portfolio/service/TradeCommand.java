package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.TransactionType;

import java.math.BigDecimal;

/**
 * 거래 요청 한 건 (FUNC-035).
 *
 * <p><b>매수는 금액, 매도는 수량</b>이다. 그래서 둘 중 하나만 채워진다.</p>
 *
 * <ul>
 *   <li>{@code BUY} — {@code amount} 필수, {@code quantity} 금지</li>
 *   <li>{@code SELL} 주식·펀드 — {@code quantity} 필수, {@code amount} 금지</li>
 *   <li>{@code SELL} 예·적금·채권 — <b>둘 다 없음</b> (전량 해지)</li>
 * </ul>
 */
public final class TradeCommand {

    private final String idempotencyKey;
    private final TransactionType transactionType;
    private final Long productId;
    private final BigDecimal amount;
    private final BigDecimal quantity;

    public TradeCommand(
            String idempotencyKey,
            TransactionType transactionType,
            Long productId,
            BigDecimal amount,
            BigDecimal quantity
    ) {
        this.idempotencyKey = idempotencyKey;
        this.transactionType = transactionType;
        this.productId = productId;
        this.amount = amount;
        this.quantity = quantity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public boolean isBuy() {
        return transactionType == TransactionType.BUY;
    }
}
