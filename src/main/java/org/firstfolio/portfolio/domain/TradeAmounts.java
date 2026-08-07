package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * 거래 한 건의 확정된 수치 (FUNC-035).
 *
 * <p>{@code requestedAmount}와 {@code executedAmount}가 <b>다를 수 있는 경우는
 * 매수형 매수 한 곳뿐</b>이다. 정수 주수로 내림하기 때문이다 —
 * 500만원을 요청해도 241,500원짜리는 20주(483만원)까지만 산다. 남은 17만원은 현금에 남는다.</p>
 *
 * <p>{@code quantity}·{@code unitPrice}는 <b>가입형에서 null</b>이다. 예·적금·채권에는
 * 수량과 단가 개념이 없다.</p>
 */
public final class TradeAmounts {

    private final BigDecimal requestedAmount;
    private final BigDecimal executedAmount;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;

    public TradeAmounts(
            BigDecimal requestedAmount,
            BigDecimal executedAmount,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
        this.requestedAmount = requestedAmount;
        this.executedAmount = executedAmount;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** 사용자가 요청한 금액. 매도에서는 계산된 대금과 같다. */
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    /** 실제로 오간 금액. 현금이 이만큼 줄거나 는다. */
    public BigDecimal getExecutedAmount() {
        return executedAmount;
    }

    /** 매수형만. 가입형은 null. */
    public BigDecimal getQuantity() {
        return quantity;
    }

    /** 매수형만. 가입형은 null. */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
