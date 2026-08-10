package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code portfolio_transactions} 한 행. 매수·매도·자산 이벤트·지급·초기화 이력이다.
 *
 * <p>중복 방지 키가 둘이고 목적이 다르다.</p>
 * <ul>
 *   <li>{@code idempotencyKey} — <b>같은 요청</b>이 두 번 처리되는 것을 막는다 (NOT NULL, UNIQUE).
 *       거래·초기화처럼 사용자가 보내는 요청에 쓴다.</li>
 *   <li>{@code eventKey} — <b>같은 자산 이벤트</b>가 배치 재실행으로 이중 반영되는 것을 막는다
 *       (NULL 허용, UNIQUE). 이자·배당·만기에 쓴다 (FUNC-042).</li>
 * </ul>
 */
public class PortfolioTransaction {

    private Long portfolioTransactionId;
    private Long portfolioId;
    private Long holdingId;
    private Long productId;
    private TransactionType transactionType;

    /** 현금 증감 또는 거래 금액. */
    private BigDecimal amount;

    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private TransactionStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime processedAt;
    private String eventKey;
    private String idempotencyKey;

    /** 수수료·세금·계산 근거·초기화 전후 정보. */
    private String detailJson;

    private LocalDateTime createdAt;

    /**
     * 조회할 때 {@code financial_products}에서 함께 읽는 표시용 가명. 저장 대상이 아니다.
     *
     * <p>지급·초기화처럼 상품이 없는 이력에서는 null이다.</p>
     */
    private String productDisplayName;

    public Long getPortfolioTransactionId() {
        return portfolioTransactionId;
    }

    public void setPortfolioTransactionId(Long portfolioTransactionId) {
        this.portfolioTransactionId = portfolioTransactionId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Long holdingId) {
        this.holdingId = holdingId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getProductDisplayName() {
        return productDisplayName;
    }

    public void setProductDisplayName(String productDisplayName) {
        this.productDisplayName = productDisplayName;
    }
}
