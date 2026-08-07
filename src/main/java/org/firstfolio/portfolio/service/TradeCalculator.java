package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.TradeAmounts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 거래로 인한 수치 계산 (FUNC-035). <b>DB를 모른다.</b>
 *
 * <p>환산 규칙과 보유 갱신 계산을 한곳에 모은다. 여기서 나온 값이 그대로 현금·보유·이력에 반영되므로
 * 자릿수와 반올림이 어긋나면 세 테이블이 조금씩 다른 값을 갖게 된다.</p>
 *
 * <h3>자릿수</h3>
 * <ul>
 *   <li>금액 {@code DECIMAL(19,2)} · 수량 {@code DECIMAL(19,6)} · 단가·평균단가 {@code DECIMAL(19,4)}</li>
 *   <li>부동소수점 타입을 쓰지 않는다 (FUNC-036 예외/제한사항)</li>
 * </ul>
 */
@Component
public class TradeCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 6;
    private static final int PRICE_SCALE = 4;

    /**
     * 매수형 매수 — 금액을 수량으로 환산한다.
     *
     * <p><b>정수 주수로 내림</b>한다. 소수점 거래를 지원하지 않으므로 요청 금액을 다 쓰지 못하고,
     * 남는 돈은 현금에 그대로 남는다.</p>
     *
     * <p>수량이 0이면 살 것이 없다는 뜻이다 — 요청 금액이 1주 값보다 적을 때다.
     * 호출한 쪽이 거부해야 한다.</p>
     */
    public TradeAmounts buyByAmount(BigDecimal requestedAmount, BigDecimal currentPrice) {
        BigDecimal quantity = requestedAmount
                .divideToIntegralValue(currentPrice)
                .setScale(QUANTITY_SCALE, RoundingMode.DOWN);

        return new TradeAmounts(
                money(requestedAmount),
                money(quantity.multiply(currentPrice)),
                quantity,
                price(currentPrice)
        );
    }

    /** 가입형 매수 — 요청 금액이 그대로 원금이 된다. 환산이 없다. */
    public TradeAmounts subscribe(BigDecimal requestedAmount) {
        BigDecimal amount = money(requestedAmount);

        return new TradeAmounts(amount, amount, null, null);
    }

    /** 매수형 매도 — 수량을 대금으로 환산한다. 요청과 체결이 항상 같다. */
    public TradeAmounts sellByQuantity(BigDecimal quantity, BigDecimal currentPrice) {
        BigDecimal amount = money(quantity.multiply(currentPrice));

        return new TradeAmounts(
                amount,
                amount,
                quantity.setScale(QUANTITY_SCALE, RoundingMode.DOWN),
                price(currentPrice)
        );
    }

    /**
     * 가입형 매도 — 전량 해지다. 원금을 그대로 돌려준다.
     *
     * <p>중도해지 이자는 정책이 없어 1차에서 다루지 않는다. 이자는 만기에만 지급되므로
     * 평가(가입형 = 원금 기준)와도 일관된다.</p>
     */
    public TradeAmounts redeem(PortfolioHolding holding) {
        BigDecimal amount = money(holding.getPrincipalAmount());

        return new TradeAmounts(amount, amount, null, null);
    }

    /**
     * 추가 매수 후의 평균 매입 단가.
     *
     * <pre>(기존 원금 + 신규 체결액) ÷ (기존 수량 + 신규 수량)</pre>
     *
     * <p>매수형에만 의미가 있다. 가입형은 나눠 살 수 없어 {@code average_cost}가 NULL이다.</p>
     */
    public BigDecimal averageCostAfterBuy(
            BigDecimal heldQuantity,
            BigDecimal heldPrincipal,
            TradeAmounts bought
    ) {
        BigDecimal totalQuantity = heldQuantity.add(bought.getQuantity());

        if (totalQuantity.signum() == 0) {
            return null;
        }

        return heldPrincipal.add(bought.getExecutedAmount())
                .divide(totalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 부분 매도 후 남는 원금.
     *
     * <p><b>남는 쪽을 비율로 계산</b>하고 차감분은 그 나머지로 둔다. 차감분을 먼저 반올림해서 빼면
     * 반복 매도에서 오차가 쌓여 원금이 음수가 될 수 있다.</p>
     *
     * <p>평균 매입 단가는 바뀌지 않는다 — 판다고 매입 단가가 달라지지는 않는다.</p>
     */
    public BigDecimal remainingPrincipalAfterSell(
            BigDecimal heldQuantity,
            BigDecimal heldPrincipal,
            BigDecimal soldQuantity
    ) {
        BigDecimal remainingQuantity = heldQuantity.subtract(soldQuantity);

        if (remainingQuantity.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }

        return money(heldPrincipal
                .multiply(remainingQuantity)
                .divide(heldQuantity, MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal price(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
