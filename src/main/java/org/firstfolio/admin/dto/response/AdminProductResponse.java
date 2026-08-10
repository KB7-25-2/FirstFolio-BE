package org.firstfolio.admin.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 관리자용 상품 응답. <b>원상품 식별정보를 포함하므로 관리자 API에서만 쓴다</b>
 * (API_DOCS 처리 규칙: "원상품 식별정보는 ADMIN 응답에서만 제공하며 감사 대상이다").
 * 사용자 응답에는 절대 재사용하지 않는다.
 *
 * <p><b>API_DOCS 예시에 없는 필드</b>: {@code source_product_name}, {@code real_terms}.
 * 관리자가 가명을 지으려면 실제 상품명과 금리·만기를 봐야 하는데 예시 응답에는 빠져 있었다.
 * 관리자 전용 응답이라 사용자 API 계약에는 영향이 없다.</p>
 */
@Schema(description = "관리자용 상품 상세. 원상품 식별정보를 포함하므로 사용자 API에 재사용 금지")
public class AdminProductResponse {

    @Schema(description = "모의 상품 ID", example = "25")
    private Long productId;
    @Schema(description = "사용자에게 노출할 가명 상품명", example = "푸른나무 정기예금")
    private String displayName;
    @Schema(description = "자산군", example = "DEPOSIT_SAVINGS")
    private String assetType;
    @Schema(description = "사용자용 상품 설명")
    private String description;
    @Schema(description = "위험 등급", example = "LOW")
    private String riskLevel;
    @Schema(description = "원천 제공기관", example = "FINLIFE")
    private String sourceProvider;
    @Schema(description = "원상품 코드. 관리자 전용", example = "FIN-001")
    private String sourceProductCode;
    @Schema(description = "원상품명. 관리자 전용", example = "OO은행 정기예금")
    private String sourceProductName;
    @Schema(description = "원천 데이터 기준 시각", example = "2026-08-07T09:00:00")
    private LocalDateTime sourceReferenceAt;
    @Schema(description = "실제 상품 조건")
    private JsonNode realTerms;
    @Schema(description = "시간 압축한 모의 운용 조건")
    private JsonNode simulationTerms;
    @Schema(description = "공개 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
    private String status;

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

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public String getSourceProductCode() {
        return sourceProductCode;
    }

    public void setSourceProductCode(String sourceProductCode) {
        this.sourceProductCode = sourceProductCode;
    }

    public String getSourceProductName() {
        return sourceProductName;
    }

    public void setSourceProductName(String sourceProductName) {
        this.sourceProductName = sourceProductName;
    }

    public LocalDateTime getSourceReferenceAt() {
        return sourceReferenceAt;
    }

    public void setSourceReferenceAt(LocalDateTime sourceReferenceAt) {
        this.sourceReferenceAt = sourceReferenceAt;
    }

    public JsonNode getRealTerms() {
        return realTerms;
    }

    public void setRealTerms(JsonNode realTerms) {
        this.realTerms = realTerms;
    }

    public JsonNode getSimulationTerms() {
        return simulationTerms;
    }

    public void setSimulationTerms(JsonNode simulationTerms) {
        this.simulationTerms = simulationTerms;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
