package org.firstfolio.portfolio.domain;

import org.firstfolio.simulation.domain.AssetType;

import java.math.BigDecimal;

/**
 * 자산군 하나의 평가액과 비중 (FUNC-034).
 *
 * <p>비중의 분모는 <b>총자산</b>(현금 + 보유자산 평가액)이다. 그래서 비중의 합은
 * 100%가 아니라 "100% − 현금 비중"이 된다 (API_DOCS 응답 예시와 같은 계산).</p>
 */
public final class AssetAllocation {

    private final AssetType assetType;
    private final BigDecimal valuationAmount;
    private final BigDecimal ratio;

    public AssetAllocation(AssetType assetType, BigDecimal valuationAmount, BigDecimal ratio) {
        this.assetType = assetType;
        this.valuationAmount = valuationAmount;
        this.ratio = ratio;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public BigDecimal getValuationAmount() {
        return valuationAmount;
    }

    /** 백분율. {@code 33.38}은 33.38%다. */
    public BigDecimal getRatio() {
        return ratio;
    }
}
