package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.TradeAmounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeCalculatorTest {

    private final TradeCalculator calculator = new TradeCalculator();

    private static PortfolioHolding holding(String quantity, String principal) {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setQuantity(new BigDecimal(quantity));
        holding.setPrincipalAmount(new BigDecimal(principal));

        return holding;
    }

    @Test
    @DisplayName("매수형 매수는 정수 주수로 내림하고 남는 금액은 체결액에서 뺀다")
    void buysWholeSharesOnly() {
        // 5,000,000 ÷ 241,500 = 20.70... → 20주
        TradeAmounts result = calculator.buyByAmount(
                new BigDecimal("5000000.00"), new BigDecimal("241500.0000"));

        assertEquals(new BigDecimal("20.000000"), result.getQuantity());
        assertEquals(new BigDecimal("4830000.00"), result.getExecutedAmount());
        assertEquals(new BigDecimal("5000000.00"), result.getRequestedAmount());
        assertEquals(new BigDecimal("241500.0000"), result.getUnitPrice());
    }

    @Test
    @DisplayName("요청 금액이 1주 값보다 적으면 수량이 0이다 — 호출한 쪽이 거부해야 한다")
    void yieldsZeroQuantityWhenAmountIsBelowOneShare() {
        TradeAmounts result = calculator.buyByAmount(
                new BigDecimal("100000.00"), new BigDecimal("241500.0000"));

        assertEquals(0, result.getQuantity().signum());
        assertEquals(new BigDecimal("0.00"), result.getExecutedAmount());
    }

    @Test
    @DisplayName("금액이 단가로 딱 나누어떨어지면 전액이 체결된다")
    void spendsWholeAmountWhenDivisible() {
        TradeAmounts result = calculator.buyByAmount(
                new BigDecimal("1000000.00"), new BigDecimal("100000.0000"));

        assertEquals(new BigDecimal("10.000000"), result.getQuantity());
        assertEquals(result.getRequestedAmount(), result.getExecutedAmount());
    }

    @Test
    @DisplayName("가입형 매수는 요청 금액이 그대로 원금이고 수량·단가가 없다")
    void subscribesWithoutConversion() {
        TradeAmounts result = calculator.subscribe(new BigDecimal("10000000.00"));

        assertEquals(new BigDecimal("10000000.00"), result.getExecutedAmount());
        assertEquals(result.getRequestedAmount(), result.getExecutedAmount());
        assertNull(result.getQuantity());
        assertNull(result.getUnitPrice());
    }

    @Test
    @DisplayName("매수형 매도는 요청 수량과 체결이 항상 같다")
    void sellsExactlyRequestedQuantity() {
        TradeAmounts result = calculator.sellByQuantity(
                new BigDecimal("8.000000"), new BigDecimal("241500.0000"));

        assertEquals(new BigDecimal("8.000000"), result.getQuantity());
        assertEquals(new BigDecimal("1932000.00"), result.getExecutedAmount());
        assertEquals(result.getRequestedAmount(), result.getExecutedAmount(),
                "매도는 내림이 없으므로 요청과 체결이 같아야 합니다.");
    }

    @Test
    @DisplayName("가입형 매도는 원금을 그대로 돌려준다 — 중도해지 이자는 2차")
    void redeemsPrincipalOnly() {
        TradeAmounts result = calculator.redeem(holding("1.000000", "15000000.00"));

        assertEquals(new BigDecimal("15000000.00"), result.getExecutedAmount());
        assertNull(result.getQuantity());
    }

    @Test
    @DisplayName("추가 매수하면 평균 매입 단가를 다시 계산한다")
    void recalculatesAverageCostOnAdditionalBuy() {
        // 기존 10주 100만원(주당 10만) + 신규 10주 120만원(주당 12만) → 평균 11만
        TradeAmounts bought = calculator.buyByAmount(
                new BigDecimal("1200000.00"), new BigDecimal("120000.0000"));

        BigDecimal averageCost = calculator.averageCostAfterBuy(
                new BigDecimal("10.000000"), new BigDecimal("1000000.00"), bought);

        assertEquals(new BigDecimal("110000.0000"), averageCost);
    }

    @Test
    @DisplayName("보유가 없던 상품을 처음 사면 체결 단가가 곧 평균 단가다")
    void averageCostEqualsUnitPriceOnFirstBuy() {
        TradeAmounts bought = calculator.buyByAmount(
                new BigDecimal("1000000.00"), new BigDecimal("100000.0000"));

        BigDecimal averageCost = calculator.averageCostAfterBuy(
                BigDecimal.ZERO, new BigDecimal("0.00"), bought);

        assertEquals(new BigDecimal("100000.0000"), averageCost);
    }

    @Test
    @DisplayName("부분 매도하면 남은 수량 비율만큼 원금이 남는다")
    void keepsPrincipalProportionalToRemainingQuantity() {
        BigDecimal remaining = calculator.remainingPrincipalAfterSell(
                new BigDecimal("10.000000"), new BigDecimal("1000000.00"), new BigDecimal("3.000000"));

        assertEquals(new BigDecimal("700000.00"), remaining);
    }

    @Test
    @DisplayName("전량 매도하면 원금이 0이 된다")
    void leavesNoPrincipalAfterSellingAll() {
        BigDecimal remaining = calculator.remainingPrincipalAfterSell(
                new BigDecimal("10.000000"), new BigDecimal("1000000.00"), new BigDecimal("10.000000"));

        assertEquals(new BigDecimal("0.00"), remaining);
    }

    @Test
    @DisplayName("나눠서 전량 매도해도 원금이 음수가 되지 않는다")
    void neverDrivesPrincipalNegativeAcrossRepeatedSells() {
        // 나누어떨어지지 않는 수량으로 반복 매도 — 반올림 오차가 쌓이는지 본다.
        BigDecimal quantity = new BigDecimal("7.000000");
        BigDecimal principal = new BigDecimal("1000000.00");

        for (int i = 0; i < 7; i++) {
            BigDecimal remaining = calculator.remainingPrincipalAfterSell(
                    quantity, principal, new BigDecimal("1.000000"));

            assertTrue(remaining.signum() >= 0, "원금이 음수가 되면 안 됩니다: " + remaining);

            quantity = quantity.subtract(new BigDecimal("1.000000"));
            principal = remaining;
        }

        assertEquals(new BigDecimal("0.00"), principal, "마지막 한 주까지 팔면 0이어야 합니다.");
    }
}
