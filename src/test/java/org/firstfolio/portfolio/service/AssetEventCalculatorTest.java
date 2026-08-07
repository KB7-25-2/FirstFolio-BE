package org.firstfolio.portfolio.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.portfolio.domain.AssetEventBasis;
import org.firstfolio.portfolio.domain.ScheduledAssetEvent;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.simulation.domain.BondRealTerms;
import org.firstfolio.simulation.domain.RealTerms;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 이자·만기 일정 계산 (FUNC-041).
 *
 * <p>조건은 <b>실제로 등록된 상품 5종의 값</b>을 쓴다. 만들어 낸 숫자로 검산하면 정작 서비스에
 * 들어 있는 조건에서 어떻게 되는지는 확인되지 않는다.</p>
 */
class AssetEventCalculatorTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("10000000.00");
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 8, 7, 3, 0);

    private final AssetEventCalculator calculator = new AssetEventCalculator(12);

    @Test
    @DisplayName("예금은 만기에 이자와 원금을 한 번씩 받는다")
    void paysDepositInterestOnceAtMaturity() {
        // 실제 상품: 12개월 만기 2.4% → 서비스 내 288시간(12일)
        List<ScheduledAssetEvent> events = calculator.schedule(
                deposit("2.4", 12, "SIMPLE"), PRINCIPAL, OPENED_AT);

        assertEquals(2, events.size());

        ScheduledAssetEvent interest = events.get(0);

        assertEquals(TransactionType.INTEREST, interest.getType());
        // 10,000,000 × 2.4% × 12/12
        assertEquals(new BigDecimal("240000.00"), interest.getAmount());
        assertEquals(AssetEventBasis.SIMPLE_INTEREST, interest.getBasis());
        assertEquals(12, interest.getPeriodMonths());
        assertEquals(OPENED_AT.plusHours(288), interest.getScheduledAt());

        ScheduledAssetEvent maturity = events.get(1);

        assertEquals(TransactionType.MATURITY, maturity.getType());
        assertEquals(PRINCIPAL, maturity.getAmount());
        assertEquals(AssetEventBasis.PRINCIPAL_RETURN, maturity.getBasis());
        assertEquals(OPENED_AT.plusHours(288), maturity.getScheduledAt());
        assertNull(maturity.getRatePercent(), "원금 반환에는 이율이 없습니다.");
    }

    @Test
    @DisplayName("적금도 일시납으로 보므로 예금과 계산이 같다 — 가정치")
    void treatsInstallmentSavingsAsLumpSum() {
        RealTerms savings = terms("2.4", 12, "SIMPLE");
        savings.setReserveType("FIXED");

        List<ScheduledAssetEvent> events =
                calculator.schedule(AssetEventTerms.of(savings, simulation(288)), PRINCIPAL, OPENED_AT);

        assertEquals(new BigDecimal("240000.00"), events.get(0).getAmount());
    }

    @Test
    @DisplayName("이표채는 주기마다 받고, 마지막 회차가 만기 시각에 온다")
    void paysCouponsOnEveryInterval() {
        // 실제 상품: 9개월 만기 1.921%, 3개월 주기 → 서비스 내 216시간, 이표 72시간마다
        List<ScheduledAssetEvent> events = calculator.schedule(
                bond("1.921", 9, 3, "이표채"), PRINCIPAL, OPENED_AT);

        assertEquals(4, events.size(), "이자 3회 + 만기 1회");

        // 10,000,000 × 1.921% × 3/12
        assertEquals(new BigDecimal("48025.00"), events.get(0).getAmount());
        assertEquals(OPENED_AT.plusHours(72), events.get(0).getScheduledAt());
        assertEquals(new BigDecimal("48025.00"), events.get(1).getAmount());
        assertEquals(OPENED_AT.plusHours(144), events.get(1).getScheduledAt());
        assertEquals(new BigDecimal("48025.00"), events.get(2).getAmount());
        assertEquals(OPENED_AT.plusHours(216), events.get(2).getScheduledAt());

        assertEquals(TransactionType.MATURITY, events.get(3).getType());
        assertEquals(OPENED_AT.plusHours(216), events.get(3).getScheduledAt());
    }

    @Test
    @DisplayName("주기로 나누어떨어지지 않으면 남는 기간을 만기에 일할로 준다")
    void proratesTheFinalCoupon() {
        // 실제 상품: 32개월 만기 3.47% 국고채, 12개월 주기 → 12·24개월째에 받고 8개월이 남는다
        List<ScheduledAssetEvent> events = calculator.schedule(
                bond("3.47", 32, 12, "이표채"), PRINCIPAL, OPENED_AT);

        assertEquals(4, events.size(), "이자 3회 + 만기 1회");

        assertEquals(new BigDecimal("347000.00"), events.get(0).getAmount());
        assertEquals(12, events.get(0).getPeriodMonths());
        assertEquals(OPENED_AT.plusHours(288), events.get(0).getScheduledAt());

        assertEquals(new BigDecimal("347000.00"), events.get(1).getAmount());
        assertEquals(OPENED_AT.plusHours(576), events.get(1).getScheduledAt());

        // 남은 8개월: 10,000,000 × 3.47% × 8/12 = 231,333.33
        assertEquals(new BigDecimal("231333.33"), events.get(2).getAmount());
        assertEquals(8, events.get(2).getPeriodMonths());
        assertEquals(OPENED_AT.plusHours(768), events.get(2).getScheduledAt());

        // 합계가 만기 전체 기간(32개월)의 이자와 같아야 한다. 정규 1회를 더 주면 40개월치가 된다.
        assertEquals(new BigDecimal("925333.33"), totalInterest(events));
    }

    @Test
    @DisplayName("지급 주기가 만기보다 길면 중간 지급 없이 만기에 잔여 기간만큼만 준다")
    void skipsInterimCouponsWhenIntervalOutlivesMaturity() {
        // 실제 상품: 잔존 2개월인데 제공처가 3개월 주기로 준다
        List<ScheduledAssetEvent> events = calculator.schedule(
                bond("2.856", 2, 3, "이표채"), PRINCIPAL, OPENED_AT);

        assertEquals(2, events.size());
        // 10,000,000 × 2.856% × 2/12
        assertEquals(new BigDecimal("47600.00"), events.get(0).getAmount());
        assertEquals(2, events.get(0).getPeriodMonths());
        assertEquals(OPENED_AT.plusHours(48), events.get(0).getScheduledAt());
    }

    @Test
    @DisplayName("복리채는 만기에 한 번 받고, 같은 조건 이표채보다 많이 받는다")
    void compoundsInterestUntilMaturity() {
        // 실제 상품 쌍: 32개월 만기 3.47%로 조건이 같고 이자 방식만 다르다
        List<ScheduledAssetEvent> compound = calculator.schedule(
                bond("3.47", 32, null, "복리채"), PRINCIPAL, OPENED_AT);
        List<ScheduledAssetEvent> coupon = calculator.schedule(
                bond("3.47", 32, 12, "이표채"), PRINCIPAL, OPENED_AT);

        assertEquals(2, compound.size(), "중간 지급이 없어 이자 1회 + 만기 1회");
        assertEquals(AssetEventBasis.COMPOUND_INTEREST, compound.get(0).getBasis());
        assertEquals(OPENED_AT.plusHours(768), compound.get(0).getScheduledAt());

        // 1.0347^2 × (1 + 3.47% × 8/12) = 1.09537073... → 953,707.31
        assertEquals(new BigDecimal("953707.31"), compound.get(0).getAmount());

        assertTrue(
                totalInterest(compound).compareTo(totalInterest(coupon)) > 0,
                "복리채가 이표채보다 많이 받아야 두 상품을 나란히 둔 의미가 있습니다."
        );
    }

    @Test
    @DisplayName("이자가 항상 만기보다 앞에 온다 — 만기가 먼저 처리되면 보유가 닫힌 뒤에 이자를 넣게 된다")
    void putsInterestBeforeMaturity() {
        List<ScheduledAssetEvent> events = calculator.schedule(
                bond("3.47", 32, 12, "이표채"), PRINCIPAL, OPENED_AT);

        assertEquals(
                TransactionType.MATURITY,
                events.get(events.size() - 1).getType(),
                "만기는 언제나 마지막입니다."
        );

        for (int index = 0; index < events.size() - 1; index++) {
            assertEquals(TransactionType.INTEREST, events.get(index).getType());
            assertTrue(
                    !events.get(index).getScheduledAt()
                            .isAfter(events.get(events.size() - 1).getScheduledAt()),
                    "이자 예정 시각이 만기를 넘을 수 없습니다."
            );
        }
    }

    @Test
    @DisplayName("이율이나 만기를 알 수 없으면 가입을 거부한다 — 0원짜리 일정을 만들지 않는다")
    void rejectsUnusableTerms() {
        assertThrows(ApiException.class, () -> calculator.schedule(
                deposit(null, 12, "SIMPLE"), PRINCIPAL, OPENED_AT));

        assertThrows(ApiException.class, () -> calculator.schedule(
                deposit("2.4", 0, "SIMPLE"), PRINCIPAL, OPENED_AT));

        assertThrows(ApiException.class, () -> calculator.schedule(
                deposit("2.4", 12, "SIMPLE"), BigDecimal.ZERO, OPENED_AT));
    }

    @Test
    @DisplayName("예·적금도 복리로 등록돼 있으면 복리로 계산한다")
    void honoursCompoundDeposits() {
        List<ScheduledAssetEvent> events = calculator.schedule(
                deposit("2.4", 24, "COMPOUND"), PRINCIPAL, OPENED_AT);

        assertEquals(AssetEventBasis.COMPOUND_INTEREST, events.get(0).getBasis());
        // 1.024^2 − 1 = 0.048576 → 485,760
        assertEquals(new BigDecimal("485760.00"), events.get(0).getAmount());
    }

    // ------------------------------------------------------------------ 조건 만들기

    private static AssetEventTerms deposit(String ratePercent, int maturityMonths, String rateType) {
        return AssetEventTerms.of(
                terms(ratePercent, maturityMonths, rateType),
                simulation(maturityMonths * 24)
        );
    }

    private static RealTerms terms(String ratePercent, int maturityMonths, String rateType) {
        RealTerms real = new RealTerms();

        real.setInterestRate(ratePercent == null ? null : new BigDecimal(ratePercent));
        real.setMaturityMonths(maturityMonths);
        real.setInterestInterval("MATURITY");
        real.setInterestRateType(rateType);

        return real;
    }

    private static AssetEventTerms bond(
            String couponRate,
            int maturityMonths,
            Integer intervalMonths,
            String interestType
    ) {
        BondRealTerms real = new BondRealTerms();

        real.setCouponRate(new BigDecimal(couponRate));
        real.setMaturityMonths(maturityMonths);
        real.setInterestIntervalMonths(intervalMonths);
        real.setInterestType(interestType);

        return AssetEventTerms.of(real, simulation(maturityMonths * 24));
    }

    private static SimulationTerms simulation(int serviceMaturityHours) {
        SimulationTerms simulation = new SimulationTerms();

        simulation.setServiceMaturityHours(serviceMaturityHours);

        return simulation;
    }

    private static BigDecimal totalInterest(List<ScheduledAssetEvent> events) {
        return events.stream()
                .filter(event -> event.getType() == TransactionType.INTEREST)
                .map(ScheduledAssetEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
