package org.firstfolio.portfolio.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "모의 상품 거래 요청. 매수는 amount, 주식·펀드 매도는 quantity를 사용")
public class TradeRequest {

    @Schema(description = "중복 거래 방지 키", example = "trade-101-20260729-001")
    private String idempotencyKey;
    @Schema(description = "거래 유형", example = "BUY", allowableValues = {"BUY", "SELL"})
    private String transactionType;
    @Schema(description = "모의 상품 ID", example = "87")
    private Long productId;
    @Schema(description = "매수 금액(원). 매수일 때만 사용", type = "string", example = "5000000.00")
    private BigDecimal amount;
    @Schema(description = "주식·펀드 매도 수량. 가입형 상품 매도는 전량 해지이므로 생략", type = "string", example = "8.000000")
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
