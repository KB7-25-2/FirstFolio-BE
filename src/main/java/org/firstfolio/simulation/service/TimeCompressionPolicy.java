package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 실제 기간을 서비스 내 기간으로 압축한다.
 *
 * <p>확정 배율은 <b>1개월 → 1일</b>이다 (SIMULATION_POLICY_v3 2.1절).
 * 예: 실제 36개월 만기 → 서비스 내 36일.</p>
 *
 * <p>주식은 실제 시세·실제 배당 주기를 그대로 쓰는 예외 대상이라 이 정책을 적용하지 않는다
 * (v3 2.2절).</p>
 */
@Component
public class TimeCompressionPolicy {

    private final int hoursPerMonth;

    public TimeCompressionPolicy(
            @Value("${simulation.compression.hours-per-month:24}") int hoursPerMonth
    ) {
        this.hoursPerMonth = hoursPerMonth;
    }

    /**
     * @param maturityMonths 원상품의 실제 만기(개월)
     * @param compressedAt   압축을 계산한 시각(UTC)
     */
    public SimulationTerms compress(
            AssetType assetType,
            Integer maturityMonths,
            LocalDateTime compressedAt
    ) {
        if (!assetType.isTimeCompressed()) {
            throw new IllegalArgumentException(
                    assetType + "은 시간 압축 대상이 아닙니다 (SIMULATION_POLICY_v3 2.2절)."
            );
        }

        // 배율이 없거나 만기를 알 수 없으면 임의로 계산하지 않는다 (FUNC-039 예외/제한사항).
        if (maturityMonths == null || maturityMonths <= 0) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "실제 만기를 알 수 없어 서비스 내 기간을 계산할 수 없습니다."
            );
        }

        int serviceMaturityHours = maturityMonths * hoursPerMonth;

        SimulationTerms terms = new SimulationTerms();

        terms.setServiceMaturityHours(serviceMaturityHours);
        // 예·적금은 만기일시지급이라 지급 주기가 만기와 같다.
        terms.setServiceInterestIntervalHours(serviceMaturityHours);
        terms.setCompressionHoursPerMonth(hoursPerMonth);
        terms.setCompressedAt(compressedAt);

        return terms;
    }
}
