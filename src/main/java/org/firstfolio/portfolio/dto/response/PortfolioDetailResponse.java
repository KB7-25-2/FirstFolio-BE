package org.firstfolio.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 현재 포트폴리오 상세 (API_DOCS {@code GET /portfolios/current}).
 *
 * <p>합계·손익·비중은 모두 서버가 확정한 값이다. 클라이언트가 다시 계산하면 반올림 방식이
 * 달라 화면마다 숫자가 어긋난다 (FUNC-036 "모의 자산 계산의 최종 권한은 서버에 있다").</p>
 *
 * <p>상품은 가명({@code display_name})으로만 나간다. 원상품명·종목코드·내부 매핑은
 * 이 타입에 필드 자체가 없다 (FUNC-032).</p>
 */
public class PortfolioDetailResponse {

    private final Long portfolioId;
    private final Integer generationNo;
    private final BigDecimal cashBalance;
    private final List<Holding> holdings;
    private final Summary summary;
    private final List<Allocation> allocation;

    /** 이 응답을 계산한 시각. 상품별 가격 기준 시점은 {@link Holding#getValuedAt()}에 있다. */
    private final LocalDateTime valuedAt;

    public PortfolioDetailResponse(
            Long portfolioId,
            Integer generationNo,
            BigDecimal cashBalance,
            List<Holding> holdings,
            Summary summary,
            List<Allocation> allocation,
            LocalDateTime valuedAt
    ) {
        this.portfolioId = portfolioId;
        this.generationNo = generationNo;
        this.cashBalance = cashBalance;
        this.holdings = holdings;
        this.summary = summary;
        this.allocation = allocation;
        this.valuedAt = valuedAt;
    }

    /**
     * 보유 상품 한 건.
     *
     * <p>{@code valuationBasis}와 {@code valuedAt}이 평가액의 근거를 밝힌다. 기준 가격을
     * 구하지 못하면 {@code PRICE_UNAVAILABLE}과 {@code valued_at: null}이 함께 나가고
     * 평가액은 매입 원금이다 — 없는 가격을 만들어 내지 않는다 (FUNC-036).</p>
     */
    public static final class Holding {

        private final Long holdingId;
        private final Long productId;
        private final String displayName;
        private final String assetType;
        private final BigDecimal quantity;
        private final BigDecimal principalAmount;
        private final BigDecimal valuationAmount;
        private final String valuationBasis;
        private final LocalDateTime valuedAt;

        public Holding(
                Long holdingId,
                Long productId,
                String displayName,
                String assetType,
                BigDecimal quantity,
                BigDecimal principalAmount,
                BigDecimal valuationAmount,
                String valuationBasis,
                LocalDateTime valuedAt
        ) {
            this.holdingId = holdingId;
            this.productId = productId;
            this.displayName = displayName;
            this.assetType = assetType;
            this.quantity = quantity;
            this.principalAmount = principalAmount;
            this.valuationAmount = valuationAmount;
            this.valuationBasis = valuationBasis;
            this.valuedAt = valuedAt;
        }

        public Long getHoldingId() {
            return holdingId;
        }

        public Long getProductId() {
            return productId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getAssetType() {
            return assetType;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getPrincipalAmount() {
            return principalAmount;
        }

        public BigDecimal getValuationAmount() {
            return valuationAmount;
        }

        public String getValuationBasis() {
            return valuationBasis;
        }

        public LocalDateTime getValuedAt() {
            return valuedAt;
        }
    }

    /** 보유자산 평가액 합계와 총자산·손익. */
    public static final class Summary {

        private final BigDecimal holdingsValue;
        private final BigDecimal totalAssets;
        private final BigDecimal profitLoss;

        /** 손익률(%). 지급받은 모의투자금 대비다. */
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        private final BigDecimal profitRate;

        public Summary(
                BigDecimal holdingsValue,
                BigDecimal totalAssets,
                BigDecimal profitLoss,
                BigDecimal profitRate
        ) {
            this.holdingsValue = holdingsValue;
            this.totalAssets = totalAssets;
            this.profitLoss = profitLoss;
            this.profitRate = profitRate;
        }

        public BigDecimal getHoldingsValue() {
            return holdingsValue;
        }

        public BigDecimal getTotalAssets() {
            return totalAssets;
        }

        public BigDecimal getProfitLoss() {
            return profitLoss;
        }

        public BigDecimal getProfitRate() {
            return profitRate;
        }
    }

    /**
     * 자산군 비중 한 칸.
     *
     * <p>{@code ratio}의 분모는 총자산이라 합이 100%가 되지 않는다. 나머지가 현금 비중이다.</p>
     */
    public static final class Allocation {

        private final String assetType;
        private final BigDecimal valuationAmount;

        /** 금액과 달리 비율은 숫자로 내보낸다 (API_DOCS 예시의 {@code 33.38}). */
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        private final BigDecimal ratio;

        public Allocation(String assetType, BigDecimal valuationAmount, BigDecimal ratio) {
            this.assetType = assetType;
            this.valuationAmount = valuationAmount;
            this.ratio = ratio;
        }

        public String getAssetType() {
            return assetType;
        }

        public BigDecimal getValuationAmount() {
            return valuationAmount;
        }

        public BigDecimal getRatio() {
            return ratio;
        }
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public Integer getGenerationNo() {
        return generationNo;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public List<Holding> getHoldings() {
        return holdings;
    }

    public Summary getSummary() {
        return summary;
    }

    public List<Allocation> getAllocation() {
        return allocation;
    }

    public LocalDateTime getValuedAt() {
        return valuedAt;
    }
}
