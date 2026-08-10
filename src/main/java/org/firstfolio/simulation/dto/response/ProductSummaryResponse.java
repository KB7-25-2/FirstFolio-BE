package org.firstfolio.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "사용자용 가명 모의 상품 요약. 원상품 식별정보는 포함하지 않음")
public class ProductSummaryResponse {

    @Schema(description = "모의 상품 ID", example = "25")
    private Long productId;
    @Schema(description = "가명 상품명", example = "푸른나무 정기예금")
    private String displayName;
    @Schema(description = "자산군", example = "DEPOSIT_SAVINGS")
    private String assetType;
    @Schema(description = "위험 등급", example = "LOW")
    private String riskLevel;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "시간 압축한 모의 운용 조건. 압축 대상이 아니면 생략")
    private JsonNode simulationTerms;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "가명 처리한 실제 상품 조건. 압축 대상이 아니면 생략")
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
