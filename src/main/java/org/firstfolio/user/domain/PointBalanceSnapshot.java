package org.firstfolio.user.domain;

import java.time.LocalDateTime;

public class PointBalanceSnapshot {

    private long userId;
    private int pointBalance;
    private long ledgerBalance;
    private LocalDateTime updatedAt;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public int getPointBalance() {
        return pointBalance;
    }

    public void setPointBalance(int pointBalance) {
        this.pointBalance = pointBalance;
    }

    public long getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(long ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
