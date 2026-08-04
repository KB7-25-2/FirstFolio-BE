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
     * <p>주식은 실제 시세·실제 배당 주기를 그대로 쓰는 예외 대상이다
     * (SIMULATION_POLICY_v3 2.2절).</p>
     */
    public boolean isTimeCompressed() {
        return this != STOCK;
    }
}
