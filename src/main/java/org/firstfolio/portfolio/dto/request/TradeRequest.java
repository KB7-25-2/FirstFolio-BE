package org.firstfolio.portfolio.dto.request;

import java.math.BigDecimal;

/**
 * 거래 요청 (API_DOCS {@code POST /portfolios/current/trades}).
 *
 * <p><b>매수는 금액, 매도는 수량</b>이라 둘 중 하나만 채워 보낸다.</p>
 *
 * <table>
 *   <tr><th></th><th>{@code amount}</th><th>{@code quantity}</th></tr>
 *   <tr><td>매수 (전 자산군)</td><td>필수</td><td>보내면 {@code 422}</td></tr>
 *   <tr><td>매도 (주식·펀드)</td><td>보내면 {@code 422}</td><td>필수</td></tr>
 *   <tr><td>매도 (예·적금·채권)</td><td>보내면 {@code 422}</td><td>보내면 {@code 422}</td></tr>
 * </table>
 *
 * <p>예·적금·채권 매도는 <b>전량 해지</b>라 아무 값도 보내지 않는다.</p>
 */
public class TradeRequest {

    private String idempotencyKey;
    private String transactionType;
    private Long productId;
    private BigDecimal amount;
    private BigDecimal quantity;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
