package org.firstfolio.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

// 포트폴리오 요약
@Schema(description = "대시보드 portfolio 섹션")
public final class PortfolioSummaryResponse {

    @Schema(description = "포트폴리오 보유 여부")
    private final boolean available;
    @Schema(description = "available=false일 때의 사유", example = "NO_PORTFOLIO", allowableValues = {"NO_PORTFOLIO"})
    private final String reason;
    @Schema(description = "총자산", type = "string", example = "31250000.00")
    private final BigDecimal totalAssets;
    @Schema(description = "최초 지급 모의투자금 대비 손익", type = "string", example = "1250000.00")
    private final BigDecimal profitLoss;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
    @Schema(description = "손익률(%)", example = "4.17")
    private final BigDecimal profitRate;
    @Schema(description = "자산군별 비중. 합계의 나머지는 현금 비중")
    private final List<Allocation> allocation;

    public PortfolioSummaryResponse(
        boolean available,
        String reason,
        BigDecimal totalAssets,
        BigDecimal profitLoss,
        BigDecimal profitRate,
        List<Allocation> allocation
    ) {
        this.available = available;
        this.reason = reason;
        this.totalAssets = totalAssets;
        this.profitLoss = profitLoss;
        this.profitRate = profitRate;
        this.allocation = allocation;
    }

    public static PortfolioSummaryResponse unavailable(String reason) {
        return new PortfolioSummaryResponse(false, reason, null, null, null, null);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getReason() {
        return reason;
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

    public List<Allocation> getAllocation() {
        return allocation;
    }

    @Schema(description = "자산군별 평가 비중")
    public static final class Allocation {

        @Schema(description = "자산군", example = "DEPOSIT_SAVINGS")
        private final String assetType;
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        @Schema(description = "총자산 대비 비중(%)", example = "33.38")
        private final BigDecimal ratio;

        public Allocation(String assetType, BigDecimal ratio) {
            this.assetType = assetType;
            this.ratio = ratio;
        }

        public String getAssetType() {
            return assetType;
        }

        public BigDecimal getRatio() {
            return ratio;
        }
    }
}
