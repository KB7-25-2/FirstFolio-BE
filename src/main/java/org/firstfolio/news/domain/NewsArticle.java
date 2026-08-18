package org.firstfolio.news.domain;

import java.time.LocalDateTime;

/**
 * {@code news_articles} 한 행.
 *
 * <p>{@code financialNewsId}는 FE가 기대하는 필드명을 그대로 식별자명으로 쓴다
 * ({@code news-scrap-dashboard-design.md} 참고).</p>
 */
public class NewsArticle {

    private Long financialNewsId;
    private String title;
    private String summary;
    private String imageUrl;
    private String sourceName;
    private String sourceUrl;
    private LocalDateTime sourcePublishedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getFinancialNewsId() {
        return financialNewsId;
    }

    public void setFinancialNewsId(Long financialNewsId) {
        this.financialNewsId = financialNewsId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDateTime getSourcePublishedAt() {
        return sourcePublishedAt;
    }

    public void setSourcePublishedAt(LocalDateTime sourcePublishedAt) {
        this.sourcePublishedAt = sourcePublishedAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
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
