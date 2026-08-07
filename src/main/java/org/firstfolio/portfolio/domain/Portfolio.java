package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code portfolios} 한 행. 사용자별 모의 포트폴리오와 현재 모의 현금이다.
 *
 * <p>파산 신청으로 초기화하면 기존 세대를 {@code CLOSED}로 닫고 {@code generationNo}를
 * 올린 새 행을 만든다. 사용자당 {@code ACTIVE}는 하나뿐이다 (FUNC-037).</p>
 *
 * <p><b>모의투자금은 포인트와 완전히 다른 재화다.</b> 포인트 잔액은 {@code users} 테이블에
 * 있고 여기 섞이지 않는다 (PROJECT_SPEC 18-5).</p>
 */
public class Portfolio {

    private Long portfolioId;
    private Long userId;
    private Integer generationNo;
    private PortfolioStatus status;

    /** 최초·재설정 시 지급된 금액. 손익 계산의 기준선이 된다 (FUNC-036). */
    private BigDecimal initialAmount;

    private BigDecimal cashBalance;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getGenerationNo() {
        return generationNo;
    }

    public void setGenerationNo(Integer generationNo) {
        this.generationNo = generationNo;
    }

    public PortfolioStatus getStatus() {
        return status;
    }

    public void setStatus(PortfolioStatus status) {
        this.status = status;
    }

    public BigDecimal getInitialAmount() {
        return initialAmount;
    }

    public void setInitialAmount(BigDecimal initialAmount) {
        this.initialAmount = initialAmount;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
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
}
