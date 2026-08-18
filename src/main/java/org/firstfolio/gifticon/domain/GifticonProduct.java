package org.firstfolio.gifticon.domain;

import java.time.LocalDateTime;

public class GifticonProduct {

    private Long gifticonProductId;
    private String name;
    private String brandName;
    private String category;
    private int faceValueKrw;
    private int requiredPoints;
    private String status;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getGifticonProductId() { return gifticonProductId; }
    public void setGifticonProductId(Long gifticonProductId) { this.gifticonProductId = gifticonProductId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getFaceValueKrw() { return faceValueKrw; }
    public void setFaceValueKrw(int faceValueKrw) { this.faceValueKrw = faceValueKrw; }
    public int getRequiredPoints() { return requiredPoints; }
    public void setRequiredPoints(int requiredPoints) { this.requiredPoints = requiredPoints; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
