package org.firstfolio.simulation.domain;

import java.time.LocalDateTime;

/**
 * 주식의 시뮬레이션 조건. {@code financial_products.simulation_terms_json}에 저장한다.
 *
 * <p>주식은 <b>시간 압축 정책의 예외</b>다. 실제 시세와 실제 배당 주기를 그대로 쓰므로
 * 압축 배율 필드가 없다 (SIMULATION_POLICY_v3 2.2절).</p>
 *
 * <p>컬럼이 {@code NOT NULL}이라 무언가는 넣어야 하는데, 빈 객체를 두면 나중에 "압축을
 * 안 한 것"인지 "계산이 실패한 것"인지 구분할 수 없다. 그래서 압축 예외임을 명시적으로
 * 남긴다. 조회 응답에서 주식의 {@code simulation_terms}/{@code real_terms}를 생략할 때도
 * 이 플래그로 판단한다 (API_SPEC_CHANGES 5번).</p>
 */
public class StockSimulationTerms {

    /** 항상 false. 주식은 압축하지 않는다. */
    private final boolean timeCompressed = false;

    /** 압축하지 않는 이유. */
    private final String reason = "STOCK_REALTIME_PRICE";

    private LocalDateTime registeredAt;

    public boolean isTimeCompressed() {
        return timeCompressed;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
}
