package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 포트폴리오 전체의 평가 결과 (FUNC-036).
 *
 * <p>모의 자산 계산의 최종 권한은 서버에 있다. 클라이언트가 다시 더하지 않아도 되도록
 * 합계·손익·비중까지 서버가 확정해서 준다.</p>
 *
 * <p>{@code hasStalePrice}가 true면 기준 가격을 못 구한 보유가 섞여 있다는 뜻이다.
 * 총자산 숫자만 보면 정상과 구분되지 않으므로 따로 알린다.</p>
 */
public final class PortfolioValuation {

    private final Portfolio portfolio;
    private final List<HoldingValuation> holdings;
    private final List<AssetAllocation> allocations;
    private final BigDecimal cashBalance;
    private final BigDecimal holdingsValue;
    private final BigDecimal totalAssets;
    private final BigDecimal profitLoss;
    private final BigDecimal profitRate;
    private final LocalDateTime valuedAt;

    public PortfolioValuation(
            Portfolio portfolio,
            List<HoldingValuation> holdings,
            List<AssetAllocation> allocations,
            BigDecimal cashBalance,
            BigDecimal holdingsValue,
            BigDecimal totalAssets,
            BigDecimal profitLoss,
            BigDecimal profitRate,
            LocalDateTime valuedAt
    ) {
        this.portfolio = portfolio;
        this.holdings = holdings;
        this.allocations = allocations;
        this.cashBalance = cashBalance;
        this.holdingsValue = holdingsValue;
        this.totalAssets = totalAssets;
        this.profitLoss = profitLoss;
        this.profitRate = profitRate;
        this.valuedAt = valuedAt;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    /** 모의 현금. 포인트와 섞이지 않는 별개의 재화다. */
    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public List<HoldingValuation> getHoldings() {
        return holdings;
    }

    public List<AssetAllocation> getAllocations() {
        return allocations;
    }

    /** 보유 상품 평가액의 합. 현금은 포함하지 않는다. */
    public BigDecimal getHoldingsValue() {
        return holdingsValue;
    }

    /** 모의 현금 + 보유자산 평가액. */
    public BigDecimal getTotalAssets() {
        return totalAssets;
    }

    /** 총자산 − 지급받은 모의투자금. 음수면 손실이다. */
    public BigDecimal getProfitLoss() {
        return profitLoss;
    }

    /** 손익률(%). {@code -2.35}는 2.35% 손실이다. */
    public BigDecimal getProfitRate() {
        return profitRate;
    }

    /** 이 평가를 계산한 시각(UTC). 상품별 가격 기준 시점은 각 보유가 따로 갖는다. */
    public LocalDateTime getValuedAt() {
        return valuedAt;
    }

    /** 기준 가격을 구하지 못한 보유가 하나라도 있는지. */
    public boolean hasStalePrice() {
        for (HoldingValuation holding : holdings) {
            if (holding.getBasis() == ValuationBasis.PRICE_UNAVAILABLE) {
                return true;
            }
        }

        return false;
    }
}
