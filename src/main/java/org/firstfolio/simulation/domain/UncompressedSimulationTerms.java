package org.firstfolio.simulation.domain;

import java.time.LocalDateTime;

/**
 * 시간 압축을 적용하지 않는 상품의 시뮬레이션 조건.
 * {@code financial_products.simulation_terms_json}에 저장한다.
 *
 * <p>주식과 ETF는 <b>만기가 없고 실시간 시세로 가격이 결정</b>되어 압축할 기간 자체가 없다.
 * 주식은 SIMULATION_POLICY_v3 2.2절이 예외로 명시하고, ETF는 v3가 "펀드"를 만기 있는
 * 공모펀드로 상정했다가 실제 데이터를 ETF로 대체하면서 생긴 어긋남이라 같은 처리를 한다.</p>
 *
 * <p>컬럼이 {@code NOT NULL}이라 무언가는 넣어야 하는데, 빈 객체를 두면 나중에 "압축을
 * 안 한 것"인지 "계산이 실패한 것"인지 구분할 수 없다. 그래서 압축 예외임을 명시적으로
 * 남긴다. 조회 응답에서 {@code simulation_terms}/{@code real_terms}를 생략할 때도
 * 이 플래그로 판단한다 (API_SPEC_CHANGES 5번).</p>
 */
public class UncompressedSimulationTerms {

    public static final String REASON_STOCK = "STOCK_REALTIME_PRICE";
    public static final String REASON_ETF = "ETF_REALTIME_PRICE";

    /** 항상 false. 압축하지 않는다. */
    private final boolean timeCompressed = false;

    private final String reason;

    private LocalDateTime registeredAt;

    public UncompressedSimulationTerms(String reason) {
        this.reason = reason;
    }

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
