package org.firstfolio.simulation.domain;

import java.time.LocalDateTime;

/**
 * 서비스 안에서 쓰는 압축된 조건. {@code financial_products.simulation_terms_json}에 저장한다.
 *
 * <p><b>이 값이 압축 조건의 유일한 기준이다.</b> {@code system_policies}의 SIMULATION 정책이
 * 이 값을 덮어쓰지 않는다 (SIMULATION_POLICY_v3 2.1절, API_DOCS PATCH 처리 규칙).</p>
 *
 * <p>주식은 압축 대상이 아니라 이 값을 만들지 않는다 (v3 2.2절).</p>
 */
public class SimulationTerms {

    private Integer serviceMaturityHours;

    /** 이자 지급 주기의 압축값. 만기일시지급이라 만기와 같다. */
    private Integer serviceInterestIntervalHours;

    /** 적용한 압축 배율. 1개월 → 1일이면 24. 계산 근거를 데이터에 남긴다 (FUNC-039). */
    private Integer compressionHoursPerMonth;

    /** 압축을 계산한 시각(UTC). */
    private LocalDateTime compressedAt;

    public Integer getServiceMaturityHours() {
        return serviceMaturityHours;
    }

    public void setServiceMaturityHours(Integer serviceMaturityHours) {
        this.serviceMaturityHours = serviceMaturityHours;
    }

    public Integer getServiceInterestIntervalHours() {
        return serviceInterestIntervalHours;
    }

    public void setServiceInterestIntervalHours(Integer serviceInterestIntervalHours) {
        this.serviceInterestIntervalHours = serviceInterestIntervalHours;
    }

    public Integer getCompressionHoursPerMonth() {
        return compressionHoursPerMonth;
    }

    public void setCompressionHoursPerMonth(Integer compressionHoursPerMonth) {
        this.compressionHoursPerMonth = compressionHoursPerMonth;
    }

    public LocalDateTime getCompressedAt() {
        return compressedAt;
    }

    public void setCompressedAt(LocalDateTime compressedAt) {
        this.compressedAt = compressedAt;
    }
}
