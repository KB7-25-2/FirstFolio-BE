package org.firstfolio.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자용 모의 상품 상세 (API_DOCS {@code GET /financial-products/{product_id}}).
 *
 * <p>{@link ProductSummaryResponse}와 같은 이유로 관리자 응답과 타입을 분리한다.
 * {@code source_product_name} 같은 원상품 식별 필드를 아예 두지 않는다 (FUNC-032).</p>
 *
 * <p>{@code currentPrice}/{@code priceReferenceAt}는 <b>저장된 기준 가격이 있을 때만</b> 채운다.
 * 없으면 두 필드를 생략한다 — 임의 값을 만들지 않는다 (FUNC-032/036).</p>
 */
public class ProductDetailResponse {

    private Long productId;
    private String displayName;
    private String assetType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String description;

    private String riskLevel;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal currentPrice;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime priceReferenceAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonNode simulationTerms;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonNode realTerms;

    private Source source;

    /**
     * 사용자에게 공개 가능한 출처 정보만 담는다.
     */
    public static class Source {

        private final String provider;
        private final LocalDateTime referenceAt;

        public Source(String provider, LocalDateTime referenceAt) {
            this.provider = provider;
            this.referenceAt = referenceAt;
        }

        public String getProvider() {
            return provider;
        }

        public LocalDateTime getReferenceAt() {
            return referenceAt;
        }
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public LocalDateTime getPriceReferenceAt() {
        return priceReferenceAt;
    }

    public void setPriceReferenceAt(LocalDateTime priceReferenceAt) {
        this.priceReferenceAt = priceReferenceAt;
    }

    public JsonNode getSimulationTerms() {
        return simulationTerms;
    }

    public void setSimulationTerms(JsonNode simulationTerms) {
        this.simulationTerms = simulationTerms;
    }

    public JsonNode getRealTerms() {
        return realTerms;
    }

    public void setRealTerms(JsonNode realTerms) {
        this.realTerms = realTerms;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }
}
