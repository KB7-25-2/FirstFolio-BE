package org.firstfolio.portfolio.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.AssetEventBasis;
import org.firstfolio.portfolio.domain.ScheduledAssetEvent;
import org.firstfolio.portfolio.domain.TransactionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 가입형 상품의 이자·만기 일정을 계산한다 (FUNC-041). <b>DB도 시계도 모른다.</b>
 *
 * <h3>이 값들은 가정치다</h3>
 *
 * <p>이자 계산식은 확정된 정책이 없다 — FSD는 <i>"별도 정책으로 관리"</i>라고만 하고
 * SIMULATION_POLICY_v3에도 없다. 아래는 실제 금융상품의 관행에 맞춰 우리가 정한 것이고,
 * 근거를 {@link ScheduledAssetEvent}에 담아 {@code detail_json}으로 남긴다.</p>
 *
 * <table border="1">
 *   <caption>지급 시점과 계산식</caption>
 *   <tr><th>상품</th><th>지급 시점</th><th>계산식</th></tr>
 *   <tr><td>예·적금</td><td>만기 1회</td><td>원금 × 연이율 × (만기개월 ÷ 12)</td></tr>
 *   <tr><td>채권(이표채)</td><td>주기마다</td><td>원금 × 쿠폰금리 × (주기개월 ÷ 12)</td></tr>
 *   <tr><td>채권(이표채) 마지막</td><td>만기</td><td>원금 × 쿠폰금리 × (<b>잔여</b>개월 ÷ 12)</td></tr>
 *   <tr><td>채권(복리채)</td><td>만기 1회</td><td>원금 × ((1+r)<sup>n</sup> × (1 + r × 잔여 ÷ 12)) − 원금</td></tr>
 *   <tr><td>만기</td><td>만기</td><td>원금을 그대로 현금으로</td></tr>
 * </table>
 *
 * <h3>마지막 이표를 일할로 주는 이유</h3>
 *
 * <p>32개월 만기에 12개월 주기면 12·24개월째에 받고 <b>8개월이 남는다.</b> 남은 기간만큼
 * 일할로 주면 총 이자가 정확히 32개월치가 된다. 정규 1회를 더 주면 40개월치를 주게 된다.</p>
 *
 * <h3>부동소수점을 쓰지 않는다</h3>
 *
 * <p>복리를 {@code Math.pow(1 + r, 32.0 / 12)}로 계산하면 double이 들어온다 —
 * 금액 계산에 부동소수점을 쓰지 않는다는 제약(FUNC-036)에 걸린다. 그래서
 * <b>정수 연차는 {@link BigDecimal#pow(int)}로 정확히</b> 계산하고, 12개월로 나누어떨어지지 않고
 * 남는 개월만 단리로 붙인다. 실제 복리채의 관행과도 맞는다.</p>
 */
@Component
public class AssetEventCalculator {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    /** 이율 계산 중간값의 자릿수. 마지막에 원 단위로 반올림한다. */
    private static final MathContext RATE_MATH = MathContext.DECIMAL64;

    private final int compoundingMonths;

    /**
     * @param compoundingMonths 복리채가 이자를 <b>계산</b>하는 주기(개월).
     *                          제공처({@code 금융위원회_채권기본정보})의 {@code intPayCyclCtt}가
     *                          복리채에도 12개월로 오는데, 우리는 그 값을 저장하지 않기로 했다 —
     *                          지급 주기로 오해하게 만들기 때문이다. 그래서 <b>12를 가정치</b>로 둔다.
     */
    public AssetEventCalculator(
            @Value("${simulation.interest.compounding-months:12}") int compoundingMonths
    ) {
        this.compoundingMonths = compoundingMonths;
    }

    /**
     * 가입 시점에 만드는 전 일정. <b>이자가 먼저, 만기가 마지막</b>이다.
     *
     * <p>순서가 곧 삽입 순서이고, 삽입 순서가 곧 배치의 처리 순서다. 만기 시각에는 이자와 만기가
     * 같은 초에 겹치는데, 만기가 먼저 처리되면 보유가 닫힌 뒤에 이자를 넣게 된다.</p>
     *
     * @param principal 가입 원금. 가입형은 요청 금액이 그대로 원금이다.
     * @param openedAt  가입 시각(UTC). 모든 예정 시각의 기준점이다.
     */
    public List<ScheduledAssetEvent> schedule(
            AssetEventTerms terms,
            BigDecimal principal,
            LocalDateTime openedAt
    ) {
        requireUsable(terms, principal);

        BigDecimal rate = terms.getRatePercent().divide(HUNDRED, RATE_MATH);
        LocalDateTime maturityAt = openedAt.plusHours(terms.getServiceMaturityHours());
        List<ScheduledAssetEvent> events = new ArrayList<>();

        if (terms.isCompound()) {
            events.add(interest(
                    maturityAt,
                    compoundInterest(principal, rate, terms.getMaturityMonths()),
                    AssetEventBasis.COMPOUND_INTEREST,
                    terms.getMaturityMonths(),
                    terms.getRatePercent()
            ));
        } else {
            events.addAll(couponSchedule(terms, principal, rate, openedAt, maturityAt));
        }

        events.add(new ScheduledAssetEvent(
                TransactionType.MATURITY,
                maturityAt,
                money(principal),
                AssetEventBasis.PRINCIPAL_RETURN,
                0,
                null
        ));

        return events;
    }

    /**
     * 이표 회차. 예·적금은 주기가 만기와 같아 <b>같은 반복문이 만기 1회로 끝난다.</b>
     *
     * <p>남은 기간이 한 주기에 못 미치면 반복을 멈추고, 그 나머지를 만기 시각에 몰아서 준다.
     * 그래서 지급액의 합이 언제나 만기 전체 기간의 이자와 같다.</p>
     */
    private List<ScheduledAssetEvent> couponSchedule(
            AssetEventTerms terms,
            BigDecimal principal,
            BigDecimal rate,
            LocalDateTime openedAt,
            LocalDateTime maturityAt
    ) {
        int maturityMonths = terms.getMaturityMonths();
        int intervalMonths = Math.min(terms.getIntervalMonths(), maturityMonths);
        List<ScheduledAssetEvent> events = new ArrayList<>();

        int elapsedMonths = 0;

        while (elapsedMonths + intervalMonths < maturityMonths) {
            elapsedMonths += intervalMonths;

            events.add(interest(
                    serviceTimeOf(openedAt, terms, elapsedMonths),
                    simpleInterest(principal, rate, intervalMonths),
                    AssetEventBasis.SIMPLE_INTEREST,
                    intervalMonths,
                    terms.getRatePercent()
            ));
        }

        int remainingMonths = maturityMonths - elapsedMonths;

        events.add(interest(
                maturityAt,
                simpleInterest(principal, rate, remainingMonths),
                AssetEventBasis.SIMPLE_INTEREST,
                remainingMonths,
                terms.getRatePercent()
        ));

        return events;
    }

    /**
     * 실제 경과 개월을 서비스 내 시각으로 옮긴다.
     *
     * <p>압축 배율을 따로 읽지 않고 <b>만기까지의 시간에서 비율로</b> 뽑는다. 기준을 하나로 두면
     * 중간 이표가 만기를 넘어서거나 만기 직전에 몰리는 어긋남이 생길 수 없다.</p>
     */
    private static LocalDateTime serviceTimeOf(
            LocalDateTime openedAt,
            AssetEventTerms terms,
            int elapsedMonths
    ) {
        long hours = (long) terms.getServiceMaturityHours() * elapsedMonths / terms.getMaturityMonths();

        return openedAt.plusHours(hours);
    }

    /** {@code 원금 × 연이율 × (기간개월 ÷ 12)} */
    private static BigDecimal simpleInterest(
            BigDecimal principal,
            BigDecimal rate,
            int periodMonths
    ) {
        return money(principal
                .multiply(rate, RATE_MATH)
                .multiply(new BigDecimal(periodMonths), RATE_MATH)
                .divide(MONTHS_PER_YEAR, RATE_MATH));
    }

    /**
     * 복리 이자. 정수 주기는 거듭제곱으로, 남는 개월은 단리로 계산한다.
     *
     * <pre>원금 × ((1 + r × 주기 ÷ 12)^n × (1 + r × 잔여 ÷ 12)) − 원금</pre>
     */
    private BigDecimal compoundInterest(
            BigDecimal principal,
            BigDecimal rate,
            int maturityMonths
    ) {
        int periods = maturityMonths / compoundingMonths;
        int remainingMonths = maturityMonths % compoundingMonths;

        BigDecimal periodRate = rate
                .multiply(new BigDecimal(compoundingMonths), RATE_MATH)
                .divide(MONTHS_PER_YEAR, RATE_MATH);

        BigDecimal grown = BigDecimal.ONE.add(periodRate).pow(periods, RATE_MATH);

        if (remainingMonths > 0) {
            BigDecimal stubRate = rate
                    .multiply(new BigDecimal(remainingMonths), RATE_MATH)
                    .divide(MONTHS_PER_YEAR, RATE_MATH);

            grown = grown.multiply(BigDecimal.ONE.add(stubRate), RATE_MATH);
        }

        return money(principal.multiply(grown, RATE_MATH).subtract(principal));
    }

    private static ScheduledAssetEvent interest(
            LocalDateTime scheduledAt,
            BigDecimal amount,
            AssetEventBasis basis,
            int periodMonths,
            BigDecimal ratePercent
    ) {
        return new ScheduledAssetEvent(
                TransactionType.INTEREST, scheduledAt, amount, basis, periodMonths, ratePercent
        );
    }

    /**
     * 조건이 하나라도 비면 <b>거래를 거부한다.</b>
     *
     * <p>이자 0원짜리 일정을 조용히 만들면 사용자는 만기까지 기다린 뒤에야 아무것도 못 받는다.
     * 가격이 없을 때 거래를 거부하는 것과 같은 이유다 (FUNC-036) — 임의 값을 만들지 않는다.</p>
     */
    private static void requireUsable(AssetEventTerms terms, BigDecimal principal) {
        if (terms.getMaturityMonths() <= 0 || terms.getServiceMaturityHours() <= 0) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "만기 조건을 알 수 없어 가입할 수 없습니다."
            );
        }

        if (terms.getRatePercent() == null || terms.getRatePercent().signum() <= 0) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "이율을 알 수 없어 가입할 수 없습니다."
            );
        }

        if (principal == null || principal.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "가입 원금이 필요합니다.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
