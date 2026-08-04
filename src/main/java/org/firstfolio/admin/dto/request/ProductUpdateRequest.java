package org.firstfolio.admin.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 관리자의 가명 상품 부분 수정 요청 (API_DOCS {@code PATCH /admin/financial-products/{id}}).
 *
 * <p>null인 필드는 바꾸지 않는다. 관리자는 여기서 가명({@code displayName})을 입력하고
 * {@code status}를 {@code ACTIVE}로 바꿔 공개한다.</p>
 */
public class ProductUpdateRequest {

    private String displayName;
    private String description;
    private String riskLevel;
    private JsonNode simulationTerms;

    /** {@code ACTIVE} 또는 {@code INACTIVE}. DRAFT 같은 중간 상태는 없다 (v3 6절). */
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
