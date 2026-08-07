package org.firstfolio.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 사용자용 모의 상품 요약 (API_DOCS {@code GET /financial-products}).
 *
 * <p><b>원상품 식별 정보를 담을 수 있는 필드를 아예 두지 않는다.</b>
 * {@code source_product_name}, {@code source_product_code}, {@code source_provider}는
 * 관리자 응답({@code AdminProductResponse})에만 있다. 두 응답을 한 클래스로 쓰면
 * 실수 한 번에 실제 상품명이 노출되므로 타입 자체를 분리한다 (FUNC-031/032).</p>
 *
 * <p>{@code simulationTerms}/{@code realTerms}는 <b>시간 압축 대상에만</b> 채운다.
 * 주식과 ETF는 만기가 없어 압축하지 않으므로 두 필드를 생략한다
 * (SIMULATION_POLICY_v3 2.2절, API_SPEC_CHANGES 5번).</p>
 */
public class ProductSummaryResponse {

    private Long productId;
    private String displayName;
    private String assetType;
    private String riskLevel;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonNode simulationTerms;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonNode realTerms;

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

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
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
}
