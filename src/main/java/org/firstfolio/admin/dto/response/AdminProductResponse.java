package org.firstfolio.admin.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

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
public class AdminProductResponse {

    private Long productId;
    private String displayName;
    private String assetType;
    private String description;
    private String riskLevel;
    private String sourceProvider;
    private String sourceProductCode;
    private String sourceProductName;
    private LocalDateTime sourceReferenceAt;
    private JsonNode realTerms;
    private JsonNode simulationTerms;
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
