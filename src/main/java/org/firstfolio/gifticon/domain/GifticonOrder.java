package org.firstfolio.gifticon.domain;

import java.time.LocalDateTime;

public class GifticonOrder {

    private Long gifticonOrderId;
    private long userId;
    private long gifticonProductId;
    private long gifticonCodeId;
    private long pointTransactionId;
    private int spentPoints;
    private String productSnapshotJson;
    private String idempotencyKey;
    private byte[] requestFingerprint;
    private LocalDateTime firstDisclosedAt;
    private LocalDateTime completedAt;

    public Long getGifticonOrderId() { return gifticonOrderId; }
    public void setGifticonOrderId(Long gifticonOrderId) { this.gifticonOrderId = gifticonOrderId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getGifticonProductId() { return gifticonProductId; }
    public void setGifticonProductId(long gifticonProductId) { this.gifticonProductId = gifticonProductId; }
    public long getGifticonCodeId() { return gifticonCodeId; }
    public void setGifticonCodeId(long gifticonCodeId) { this.gifticonCodeId = gifticonCodeId; }
    public long getPointTransactionId() { return pointTransactionId; }
    public void setPointTransactionId(long pointTransactionId) { this.pointTransactionId = pointTransactionId; }
    public int getSpentPoints() { return spentPoints; }
    public void setSpentPoints(int spentPoints) { this.spentPoints = spentPoints; }
    public String getProductSnapshotJson() { return productSnapshotJson; }
    public void setProductSnapshotJson(String productSnapshotJson) { this.productSnapshotJson = productSnapshotJson; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public byte[] getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(byte[] requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public LocalDateTime getFirstDisclosedAt() { return firstDisclosedAt; }
    public void setFirstDisclosedAt(LocalDateTime firstDisclosedAt) { this.firstDisclosedAt = firstDisclosedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
