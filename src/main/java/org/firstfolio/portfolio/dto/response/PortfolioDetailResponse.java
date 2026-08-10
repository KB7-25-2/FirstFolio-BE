package org.firstfolio.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "현재 활성 포트폴리오의 서버 확정 평가 결과")
public class PortfolioDetailResponse {

    @Schema(description = "활성 포트폴리오 ID", example = "8001")
    private final Long portfolioId;
    @Schema(description = "초기화로 증가하는 포트폴리오 세대 번호", example = "1")
    private final Integer generationNo;
    @Schema(description = "현재 모의 현금", type = "string", example = "2000000.00")
    private final BigDecimal cashBalance;
    @Schema(description = "보유 상품 목록. 원상품 식별정보는 포함하지 않음")
    private final List<Holding> holdings;
    @Schema(description = "보유자산·총자산·손익 요약")
    private final Summary summary;
    @Schema(description = "자산군별 배분. 비율의 나머지는 현금 비중")
    private final List<Allocation> allocation;

    /** 이 응답을 계산한 시각. 상품별 가격 기준 시점은 {@link Holding#getValuedAt()}에 있다. */
    @Schema(description = "응답 전체를 계산한 시각", example = "2026-07-29T03:00:00")
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
    @Schema(description = "가명 모의 상품 보유 내역")
    public static final class Holding {

        @Schema(description = "보유 ID", example = "8101")
        private final Long holdingId;
        @Schema(description = "모의 상품 ID", example = "25")
        private final Long productId;
        @Schema(description = "가명 상품명", example = "푸른나무 정기예금")
        private final String displayName;
        @Schema(description = "자산군", example = "DEPOSIT_SAVINGS")
        private final String assetType;
        @Schema(description = "보유 수량", type = "string", example = "1.000000")
        private final BigDecimal quantity;
        @Schema(description = "투입 원금", type = "string", example = "10000000.00")
        private final BigDecimal principalAmount;
        @Schema(description = "서버가 확정한 평가액", type = "string", example = "10080000.00")
        private final BigDecimal valuationAmount;
        @Schema(description = "평가 근거", example = "PRINCIPAL", allowableValues = {"MARKET_PRICE", "PRINCIPAL", "PRICE_UNAVAILABLE"})
        private final String valuationBasis;
        @Schema(description = "상품 평가에 사용한 가격 기준 시각. 원금 평가 또는 가격 부재 시 null", example = "2026-07-29T03:00:00")
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
    @Schema(description = "포트폴리오 평가 요약")
    public static final class Summary {

        @Schema(description = "보유 상품 평가액 합계", type = "string", example = "28200000.00")
        private final BigDecimal holdingsValue;
        @Schema(description = "현금과 보유 상품을 합한 총자산", type = "string", example = "30200000.00")
        private final BigDecimal totalAssets;
        @Schema(description = "최초 지급 모의투자금 대비 손익", type = "string", example = "200000.00")
        private final BigDecimal profitLoss;

        /** 손익률(%). 지급받은 모의투자금 대비다. */
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        @Schema(description = "최초 지급 모의투자금 대비 손익률(%)", example = "0.67")
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
    @Schema(description = "자산군별 평가액과 총자산 대비 비중")
    public static final class Allocation {

        @Schema(description = "자산군", example = "DEPOSIT_SAVINGS")
        private final String assetType;
        @Schema(description = "자산군 평가액", type = "string", example = "10080000.00")
        private final BigDecimal valuationAmount;

        /** 금액과 달리 비율은 숫자로 내보낸다 (API_DOCS 예시의 {@code 33.38}). */
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        @Schema(description = "총자산 대비 비중(%). 합계의 나머지는 현금", example = "33.38")
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
