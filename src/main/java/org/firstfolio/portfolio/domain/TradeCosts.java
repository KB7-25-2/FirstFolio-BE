package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * 거래 한 건에 붙는 비용과 그 결과 오가는 현금 (SIMULATION_POLICY_v3 3.3절).
 *
 * <p>{@link TradeAmounts}가 "얼마어치를 샀나"라면 이쪽은 <b>"그래서 현금이 얼마나 움직이나"</b>다.
 * 체결액과 현금 증감이 달라지는 이유가 전부 여기 담긴다.</p>
 *
 * <h3>매수는 더하고 매도는 뺀다</h3>
 *
 * <pre>
 * 매수  netCashAmount = 체결액 + 수수료   (현금이 이만큼 준다)
 * 매도  netCashAmount = 체결액 − 수수료   (현금이 이만큼 는다)
 * </pre>
 *
 * <p><b>부호는 담지 않는다.</b> 어느 쪽으로 움직이는지는 거래 유형이 정하고 여기에는 크기만 둔다 —
 * {@code decreaseCash}·{@code increaseCash}가 각각 양수를 받기 때문이다.</p>
 *
 * <h3>적용 요율을 함께 들고 다닌다</h3>
 *
 * <p>{@code feeRate}와 {@code policyVersion}은 <b>검산용</b>이다. 요율은 {@code system_policies}로
 * 관리되어 언제든 바뀔 수 있으므로, 금액만 남기면 나중에 "이 수수료가 왜 이 값인지"를 확인할 방법이
 * 없다. {@code policyVersion}이 {@code null}이면 저장된 정책 없이 설정 기본값으로 계산했다는 뜻이고,
 * 그 사실 자체가 이력에 남아야 한다.</p>
 *
 * <p>수수료가 붙지 않는 가입형 거래에서는 {@code feeRate}도 <b>0</b>이다 — 정책상의 요율이 아니라
 * <b>이 거래에 실제로 적용된 요율</b>을 담는다.</p>
 */
public final class TradeCosts {

    private final BigDecimal feeAmount;
    private final BigDecimal netCashAmount;
    private final BigDecimal feeRate;

    /** 적용된 정책 버전. <b>설정 기본값으로 돌면 null이다.</b> */
    private final Integer policyVersion;

    public TradeCosts(
            BigDecimal feeAmount,
            BigDecimal netCashAmount,
            BigDecimal feeRate,
            Integer policyVersion
    ) {
        this.feeAmount = feeAmount;
        this.netCashAmount = netCashAmount;
        this.feeRate = feeRate;
        this.policyVersion = policyVersion;
    }

    /** 매매 수수료. 가입형 거래에서는 {@code 0.00}이다. */
    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    /** 실제로 오가는 현금. 매수는 체결액보다 크고 매도는 작다. */
    public BigDecimal getNetCashAmount() {
        return netCashAmount;
    }

    /** 이 거래에 실제로 적용된 수수료율. 0.00015 = 0.015% */
    public BigDecimal getFeeRate() {
        return feeRate;
    }

    /** 적용된 정책 버전. 설정 기본값으로 돌면 null이다. */
    public Integer getPolicyVersion() {
        return policyVersion;
    }
}
