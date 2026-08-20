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
    private final boolean installment;
    private final int serviceMaturityHours;

    private AssetEventTerms(
            BigDecimal ratePercent,
            int maturityMonths,
            int intervalMonths,
            boolean compound,
            boolean installment,
            int serviceMaturityHours
    ) {
        this.ratePercent = ratePercent;
        this.maturityMonths = maturityMonths;
        this.intervalMonths = intervalMonths;
        this.compound = compound;
        this.installment = installment;
        this.serviceMaturityHours = serviceMaturityHours;
    }

    /**
     * 예·적금.
     *
     * <h3>적금은 적립식으로 계산한다</h3>
     *
     * <p>우리 구조에서 가입은 금액 한 번을 넣는 것이지만({@link TradeCalculator#subscribe}),
     * <b>이자까지 예금과 같게 계산하면 "금리 높은 적금이 무조건 유리"라고 가르치게 된다.</b>
     * 실제 적금은 매달 나눠 넣어 회차마다 예치 기간이 다르고, 그래서 같은 원금·기간이면
     * 이자가 예금의 절반쯤이다.</p>
     *
     * <p>그래서 <b>총 납입액을 가입 시점에 전액 받아두고, 이자만 회차별 예치 기간에 비례해</b>
     * 계산한다 ({@link org.firstfolio.portfolio.domain.AssetEventBasis#INSTALLMENT_INTEREST}).
     * 실제 적금과 총 납입액·총 이자가 같고, 다른 것은 아직 낼 차례가 아닌 돈을 그동안 다른 곳에
     * 굴릴 수 없다는 것뿐이다 — 1차 범위의 단순화로 {@code detail_json}에 남긴다.</p>
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
                isInstallment(real.getReserveType()),
                hours(simulation)
        );
    }

    /**
     * 적립식(적금)인지. <b>{@code reserveType}이 있으면 적금이다</b> —
     * finlife 응답에서 적립 유형({@code rsrvTypeNm})은 적금에만 오고 예금에는 없다
     * ({@code DepositSavingCollector.normalizeReserveType}).
     *
     * <p>정액적립식({@code FIXED})과 자유적립식({@code FLEXIBLE})을 가르지 않는다. 둘 다 매달
     * 나눠 넣는 상품이라 이자 계산이 같고, 갈리는 지점은 "납입 금액을 사용자가 정하느냐"인데
     * 우리는 어느 쪽이든 가입 시 총액을 한 번에 받는다.</p>
     */
    private static boolean isInstallment(String reserveType) {
        return reserveType != null && !reserveType.isBlank();
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
                // 채권은 매수 시점에 전액을 넣는다. 나눠 넣는 개념이 없다.
                false,
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

    /**
     * 적립식(적금)이면 {@code true}. 예금·채권은 {@code false}다.
     *
     * <p>이자 계산식이 갈리는 유일한 자리다. 예치 기간이 회차마다 다르기 때문이다.</p>
     */
    public boolean isInstallment() {
        return installment;
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
