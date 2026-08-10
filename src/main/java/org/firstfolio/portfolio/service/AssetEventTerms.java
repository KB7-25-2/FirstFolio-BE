package org.firstfolio.portfolio.service;

import org.firstfolio.simulation.domain.BondRealTerms;
import org.firstfolio.simulation.domain.RealTerms;
import org.firstfolio.simulation.domain.SimulationTerms;

import java.math.BigDecimal;

/**
 * 일정 계산에 필요한 조건만 추린 값 (FUNC-041).
 *
 * <h3>두 곳에서 온다</h3>
 *
 * <ul>
 *   <li><b>금액</b>은 {@code real_terms} — 실제 이율·만기·지급 주기로 계산한다.
 *       사용자가 배우는 것은 실제 상품의 수익이다.</li>
 *   <li><b>시각</b>은 {@code simulation_terms} — 1개월이 1일로 압축된 서비스 내 기간이다
 *       (SIMULATION_POLICY_v3 2.1절).</li>
 * </ul>
 *
 * <p>예·적금과 채권은 저장하는 조건 클래스가 달라({@link RealTerms}·{@link BondRealTerms})
 * 여기서 하나로 모은다. 이자 계산이 두 자산군에서 다른 것은 <b>복리 여부와 지급 주기뿐</b>이다.</p>
 */
public final class AssetEventTerms {

    private final BigDecimal ratePercent;
    private final int maturityMonths;
    private final int intervalMonths;
    private final boolean compound;
    private final int serviceMaturityHours;

    public AssetEventTerms(
            BigDecimal ratePercent,
            int maturityMonths,
            int intervalMonths,
            boolean compound,
            int serviceMaturityHours
    ) {
        this.ratePercent = ratePercent;
        this.maturityMonths = maturityMonths;
        this.intervalMonths = intervalMonths;
        this.compound = compound;
        this.serviceMaturityHours = serviceMaturityHours;
    }

    /**
     * 예·적금.
     *
     * <p><b>적금도 일시납으로 본다.</b> 우리 구조에서 가입은 금액 한 번을 넣는 것이라
     * ({@link TradeCalculator#subscribe}) 정기예금과 계산이 같다. 실제 월납 적금의 이자는
     * 대략 절반이지만, 매달 자동이체를 흉내 내는 기능이 없으므로 그 차이를 만들 방법이 없다.
     * 가정치임을 {@code detail_json}에 남긴다.</p>
     *
     * <p>지급 주기는 만기와 같다 — 정기예금·정기적금은 만기일시지급이다
     * ({@code real_terms.interest_interval = MATURITY}).</p>
     */
    public static AssetEventTerms of(RealTerms real, SimulationTerms simulation) {
        int maturityMonths = months(real.getMaturityMonths());

        return new AssetEventTerms(
                real.getInterestRate(),
                maturityMonths,
                maturityMonths,
                "COMPOUND".equals(real.getInterestRateType()),
                hours(simulation)
        );
    }

    /**
     * 채권.
     *
     * <p>복리채는 중간 지급이 없어 {@code interest_interval_months}를 담지 않는다
     * (사용자가 매년 이자를 받는 상품으로 오해하기 때문 — 2026-08-05 결함 기록).
     * 그래서 주기가 비어 있으면 만기와 같게 본다.</p>
     */
    public static AssetEventTerms of(BondRealTerms real, SimulationTerms simulation) {
        int maturityMonths = months(real.getMaturityMonths());
        Integer interval = real.getInterestIntervalMonths();

        return new AssetEventTerms(
                real.getCouponRate(),
                maturityMonths,
                interval == null || interval <= 0 ? maturityMonths : interval,
                real.getInterestType() != null && real.getInterestType().contains("복리"),
                hours(simulation)
        );
    }

    /** 연이율·쿠폰금리(%). 값이 없으면 null — 호출한 쪽이 거부해야 한다. */
    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    /** 실제 만기(개월). 금액 계산에 쓴다. */
    public int getMaturityMonths() {
        return maturityMonths;
    }

    /**
     * 실제 이자 지급 주기(개월).
     *
     * <p>만기보다 길 수 있다 — 잔존 2개월짜리 채권에 3개월 주기가 붙어 오는 경우가 실제로 있다.
     * 그때는 만기에 잔여 기간만큼 한 번 준다.</p>
     */
    public int getIntervalMonths() {
        return intervalMonths;
    }

    public boolean isCompound() {
        return compound;
    }

    /** 서비스 내 만기까지의 시간. 모든 예정 시각을 이 값 하나에서 뽑는다. */
    public int getServiceMaturityHours() {
        return serviceMaturityHours;
    }

    private static int months(Integer value) {
        return value == null ? 0 : value;
    }

    private static int hours(SimulationTerms simulation) {
        return simulation == null || simulation.getServiceMaturityHours() == null
                ? 0
                : simulation.getServiceMaturityHours();
    }
}
