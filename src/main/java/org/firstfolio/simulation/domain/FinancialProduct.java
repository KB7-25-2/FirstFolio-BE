package org.firstfolio.simulation.domain;

import java.time.LocalDateTime;

/**
 * {@code financial_products} 한 행. 실제 원천 정보와 가명 모의 상품을 함께 담는다.
 *
 * <p>{@code source*} 필드는 <b>내부 전용</b>이다. 사용자 API 응답에 절대 포함하지 않는다
 * (FUNC-032/038). 관리자 응답에만 노출하며 감사 대상이다.</p>
 *
 * <p>{@code realTermsJson}/{@code simulationTermsJson}은 DB의 JSON 컬럼을 문자열로 들고 있는다.
 * 구조를 다루는 쪽은 서비스 계층이다.</p>
 */
public class FinancialProduct {

    private Long productId;
    private AssetType assetType;
    private String displayName;
    private String description;
    private String sourceProvider;
    private String sourceProductCode;
    private String sourceProductName;
    private LocalDateTime sourceReferenceAt;
    private String realTermsJson;
    private String simulationTermsJson;
    private String riskLevel;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getRealTermsJson() {
        return realTermsJson;
    }

    public void setRealTermsJson(String realTermsJson) {
        this.realTermsJson = realTermsJson;
    }

    public String getSimulationTermsJson() {
        return simulationTermsJson;
    }

    public void setSimulationTermsJson(String simulationTermsJson) {
        this.simulationTermsJson = simulationTermsJson;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
