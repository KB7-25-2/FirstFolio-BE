package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * 거래에 적용할 수수료·세율 (SIMULATION_POLICY_v3 3.3절, D14).
 *
 * <table>
 *   <tr><th>항목</th><th>확정값</th><th>근거</th></tr>
 *   <tr><td>매수·매도 수수료</td><td>0.015%</td><td>v3 3.3절</td></tr>
 *   <tr><td>증권거래세</td><td>0.20%</td><td>v3 3.3절 (코스피·코스닥 동일)</td></tr>
 *   <tr><td>배당소득세</td><td>15.4%</td><td>v3 3.3절</td></tr>
 *   <tr><td>이자소득세</td><td>15.4%</td><td>D14 (v3 반영 요청 중)</td></tr>
 * </table>
 *
 * <p><b>비율이다.</b> 0.00015가 0.015%다. 퍼센트 값이 아니므로 100으로 나누지 않는다.</p>
 *
 * <p>{@code policyVersion}은 <b>이 값들이 어느 정책 버전에서 왔는지</b>다. 거래 이력에 함께
 * 남겨 두면 나중에 요율이 바뀌어도 과거 거래를 검산할 수 있다. <b>정책 행이 없어 기본값으로
 * 돈 경우에는 null</b>이며, 그 사실 자체가 이력에 남아야 한다.</p>
 */
public final class TradePolicy {

    private final BigDecimal buyFeeRate;
    private final BigDecimal sellFeeRate;
    private final BigDecimal securitiesTransactionTaxRate;
    private final BigDecimal dividendIncomeTaxRate;
    private final BigDecimal interestIncomeTaxRate;

    /** 적용된 정책 버전. <b>기본값으로 돌면 null이다.</b> */
    private final Integer policyVersion;

    public TradePolicy(
            BigDecimal buyFeeRate,
            BigDecimal sellFeeRate,
            BigDecimal securitiesTransactionTaxRate,
            BigDecimal dividendIncomeTaxRate,
            BigDecimal interestIncomeTaxRate,
            Integer policyVersion
    ) {
        this.buyFeeRate = buyFeeRate;
        this.sellFeeRate = sellFeeRate;
        this.securitiesTransactionTaxRate = securitiesTransactionTaxRate;
        this.dividendIncomeTaxRate = dividendIncomeTaxRate;
        this.interestIncomeTaxRate = interestIncomeTaxRate;
        this.policyVersion = policyVersion;
    }

    /** 매수 수수료율. 0.00015 = 0.015% */
    public BigDecimal getBuyFeeRate() {
        return buyFeeRate;
    }

    /** 매도 수수료율. 0.00015 = 0.015% */
    public BigDecimal getSellFeeRate() {
        return sellFeeRate;
    }

    /** 증권거래세율. <b>매도에만 붙는다.</b> 0.0020 = 0.20% */
    public BigDecimal getSecuritiesTransactionTaxRate() {
        return securitiesTransactionTaxRate;
    }

    /** 배당소득세율. 0.154 = 15.4% */
    public BigDecimal getDividendIncomeTaxRate() {
        return dividendIncomeTaxRate;
    }

    /** 이자소득세율. 0.154 = 15.4% */
    public BigDecimal getInterestIncomeTaxRate() {
        return interestIncomeTaxRate;
    }

    /** 적용된 정책 버전. 기본값으로 돌면 null이다. */
    public Integer getPolicyVersion() {
        return policyVersion;
    }

    /** 저장된 정책에서 왔는지. 거짓이면 설정 기본값이다. */
    public boolean isFromStoredPolicy() {
        return policyVersion != null;
    }
}
