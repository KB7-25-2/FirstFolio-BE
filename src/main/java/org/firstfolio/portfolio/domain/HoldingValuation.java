package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보유 상품 하나의 평가 결과 (FUNC-036).
 *
 * <p>{@code valuedAt}은 <b>이 상품의 평가 기준 시점</b>이다. 상품마다 가격 수집 시각이
 * 다를 수 있어 포트폴리오 전체의 계산 시각과 따로 들고 다닌다
 * (FUNC-034 "가격 기준 시점이 다른 경우 각 평가 기준 시점을 함께 표시한다").</p>
 */
public final class HoldingValuation {

    private final PortfolioHolding holding;
    private final BigDecimal valuationAmount;
    private final ValuationBasis basis;
    private final LocalDateTime valuedAt;

    public HoldingValuation(
            PortfolioHolding holding,
            BigDecimal valuationAmount,
            ValuationBasis basis,
            LocalDateTime valuedAt
    ) {
        this.holding = holding;
        this.valuationAmount = valuationAmount;
        this.basis = basis;
        this.valuedAt = valuedAt;
    }

    public PortfolioHolding getHolding() {
        return holding;
    }

    public BigDecimal getValuationAmount() {
        return valuationAmount;
    }

    public ValuationBasis getBasis() {
        return basis;
    }

    /** 기준 가격을 못 구한 경우 null이다. "지금 시각 기준"이라고 말할 수 없기 때문이다. */
    public LocalDateTime getValuedAt() {
        return valuedAt;
    }
}
