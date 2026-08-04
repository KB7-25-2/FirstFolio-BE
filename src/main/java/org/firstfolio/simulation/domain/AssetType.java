package org.firstfolio.simulation.domain;

/**
 * 모의 상품의 자산군. {@code financial_products.asset_type} CHECK 제약과 값이 같아야 한다.
 */
public enum AssetType {

    DEPOSIT_SAVINGS,
    BOND,
    STOCK,
    FUND;

    /**
     * 시간 압축 정책 적용 대상인지 여부.
     *
     * <p>압축은 "실제 만기·지급 주기를 서비스 내 기간으로 환산"하는 것이라
     * <b>만기가 있는 상품에만</b> 적용된다.</p>
     *
     * <ul>
     *   <li>{@code STOCK} — SIMULATION_POLICY_v3 2.2절이 명시한 예외. 실제 시세·배당 주기를 그대로 쓴다.</li>
     *   <li>{@code FUND} — v3 2.1절은 펀드를 압축 대상으로 두지만, 6절에서 펀드를 <b>ETF로 대체</b>하기로
     *       확정하면서 어긋났다. ETF는 만기가 없어 압축할 기간 자체가 없다. FUNC-040과
     *       {@code /internal/product-prices/refresh}도 ETF를 주식과 함께 "가격 갱신" 대상으로 다룬다.</li>
     * </ul>
     *
     * <p>압축하지 않는 상품은 조회 응답에서 {@code simulation_terms}/{@code real_terms}를 생략한다
     * (API_SPEC_CHANGES 5번).</p>
     */
    public boolean isTimeCompressed() {
        return this == DEPOSIT_SAVINGS || this == BOND;
    }
}
