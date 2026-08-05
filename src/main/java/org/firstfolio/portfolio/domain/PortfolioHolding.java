package org.firstfolio.portfolio.domain;

import org.firstfolio.simulation.domain.AssetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code portfolio_holdings} 한 행. 포트폴리오가 현재 들고 있는 상품이다.
 *
 * <p>{@code averageCost}가 NULL 허용인 것이 자산군의 성격 차이를 드러낸다. 평균 매입 단가는
 * 여러 번 나눠 살 수 있는 <b>매수형</b>(주식·펀드)에서만 의미가 있고, 한 번 가입하면 끝인
 * <b>가입형</b>(예·적금·채권)에서는 비어 있다.</p>
 */
public class PortfolioHolding {

    private Long holdingId;
    private Long portfolioId;
    private Long productId;
    private BigDecimal quantity;

    /** 투입 원금. 가입형의 평가 기준이자 매수형의 매입 원가다. */
    private BigDecimal principalAmount;

    /** 평균 매입 단가. 매수형에만 있다. */
    private BigDecimal averageCost;

    /** 가입·매수 당시의 상품·시뮬레이션 조건. 상품 조건이 바뀌어도 이 값으로 계산한다. */
    private String termsSnapshotJson;

    private HoldingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 조회할 때 {@code financial_products}에서 함께 읽는 표시용 필드다. 저장 대상이 아니다.
     *
     * <p>사용자에게 보이는 이름은 언제나 가명({@code display_name})이다.
     * 원상품명은 응답 근처에도 두지 않는다 (FUNC-032).</p>
     */
    private String productDisplayName;

    /** 〃 평가 규칙과 자산군 비중을 가르는 기준이다. */
    private AssetType productAssetType;

    public Long getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Long holdingId) {
        this.holdingId = holdingId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public String getTermsSnapshotJson() {
        return termsSnapshotJson;
    }

    public void setTermsSnapshotJson(String termsSnapshotJson) {
        this.termsSnapshotJson = termsSnapshotJson;
    }

    public HoldingStatus getStatus() {
        return status;
    }

    public void setStatus(HoldingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getProductDisplayName() {
        return productDisplayName;
    }

    public void setProductDisplayName(String productDisplayName) {
        this.productDisplayName = productDisplayName;
    }

    public AssetType getProductAssetType() {
        return productAssetType;
    }

    public void setProductAssetType(AssetType productAssetType) {
        this.productAssetType = productAssetType;
    }
}
