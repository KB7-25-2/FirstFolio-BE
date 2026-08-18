package org.firstfolio.gifticon.domain;

import java.time.LocalDateTime;

public class GifticonCode {

    private Long gifticonCodeId;
    private Long gifticonProductId;
    private byte[] codeCiphertext;
    private String codeMasked;
    private byte[] codeFingerprint;
    private String encryptionKeyVersion;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime createdAt;

    public Long getGifticonCodeId() { return gifticonCodeId; }
    public void setGifticonCodeId(Long gifticonCodeId) { this.gifticonCodeId = gifticonCodeId; }
    public Long getGifticonProductId() { return gifticonProductId; }
    public void setGifticonProductId(Long gifticonProductId) { this.gifticonProductId = gifticonProductId; }
    public byte[] getCodeCiphertext() { return codeCiphertext; }
    public void setCodeCiphertext(byte[] codeCiphertext) { this.codeCiphertext = codeCiphertext; }
    public String getCodeMasked() { return codeMasked; }
    public void setCodeMasked(String codeMasked) { this.codeMasked = codeMasked; }
    public byte[] getCodeFingerprint() { return codeFingerprint; }
    public void setCodeFingerprint(byte[] codeFingerprint) { this.codeFingerprint = codeFingerprint; }
    public String getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public void setEncryptionKeyVersion(String encryptionKeyVersion) { this.encryptionKeyVersion = encryptionKeyVersion; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
