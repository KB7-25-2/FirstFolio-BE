package org.firstfolio.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "사용자용 가명 모의 상품 상세. 원상품 식별정보는 포함하지 않음")
public class ProductDetailResponse {

    @Schema(description = "모의 상품 ID", example = "25")
    private Long productId;
    @Schema(description = "가명 상품명", example = "푸른나무 정기예금")
    private String displayName;
    @Schema(description = "자산군", example = "DEPOSIT_SAVINGS")
    private String assetType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "사용자용 상품 설명")
    private String description;

    @Schema(description = "위험 등급", example = "LOW")
    private String riskLevel;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "저장된 최신 기준 가격. 없으면 생략", type = "string", example = "241500.0000")
    private BigDecimal currentPrice;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "기준 가격 시각. 가격이 없으면 생략", example = "2026-08-07T09:00:00")
    private LocalDateTime priceReferenceAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "시간 압축한 모의 운용 조건")
    private JsonNode simulationTerms;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "가명 처리한 실제 상품 조건")
    private JsonNode realTerms;

    @Schema(description = "사용자에게 공개 가능한 데이터 출처와 기준 시점")
    private Source source;

    /**
     * 사용자에게 공개 가능한 출처 정보만 담는다.
     */
    @Schema(description = "공개 가능한 상품 데이터 출처")
    public static class Source {

        @Schema(description = "출처 제공기관", example = "FINLIFE")
        private final String provider;
        @Schema(description = "원천 데이터 기준 시각", example = "2026-08-07T09:00:00")
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
