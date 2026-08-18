package org.firstfolio.gifticon.domain;

import java.time.LocalDateTime;

public class GifticonOrderView extends GifticonOrder {

    private int balanceAfter;
    private String codeMasked;
    private String codeStatus;
    private LocalDateTime expiresAt;

    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getCodeMasked() { return codeMasked; }
    public void setCodeMasked(String codeMasked) { this.codeMasked = codeMasked; }
    public String getCodeStatus() { return codeStatus; }
    public void setCodeStatus(String codeStatus) { this.codeStatus = codeStatus; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
