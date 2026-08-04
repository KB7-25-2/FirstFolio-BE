package org.firstfolio.simulation.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code product_prices} 한 행. 주식·펀드의 시점별 기준 가격이다.
 *
 * <p>조회·평가는 이 저장된 값을 쓰고, 거래 체결가만 주문 시점에 외부 API로 확정한다
 * (SIMULATION_POLICY_v3 3.2절). 매 조회마다 외부를 부르면 Rate Limit에 걸리고
 * 제공처 장애가 그대로 전파된다.</p>
 */
public class ProductPrice {

    private Long productPriceId;
    private Long productId;
    private BigDecimal price;
    private LocalDateTime referenceAt;

    /** REAL_DATA 또는 SIMULATION. */
    private String sourceType;

    private String generationKey;
    private LocalDateTime createdAt;

    public Long getProductPriceId() {
        return productPriceId;
    }

    public void setProductPriceId(Long productPriceId) {
        this.productPriceId = productPriceId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }

    public void setReferenceAt(LocalDateTime referenceAt) {
        this.referenceAt = referenceAt;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getGenerationKey() {
        return generationKey;
    }

    public void setGenerationKey(String generationKey) {
        this.generationKey = generationKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
