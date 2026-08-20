package org.firstfolio.newsletter.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Newsletter {

    private Long newsletterId;
    private LocalDate weekStartDate;
    private String headline;
    private String financialWordsJson;
    private String issuesJson;
    private String statsJson;
    private NewsletterStatus status;
    private NewsletterGenerationType generationType;
    private long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public Newsletter() {
    }

    /**
     * AI 배치가 생성한 뉴스레터는 검수 없이 바로 노출되지 않으므로
     * {@code REVIEW} 상태로 시작한다. {@code newsletters} 테이블은
     * {@code DRAFT} 상태를 허용하지 않는다.
     */
    public static Newsletter review(
            LocalDate weekStartDate,
            String headline,
            String financialWordsJson,
            String issuesJson,
            String statsJson,
            NewsletterGenerationType generationType,
            long createdBy,
            LocalDateTime createdAt
    ) {
        Newsletter newsletter = new Newsletter();
        newsletter.weekStartDate = weekStartDate;
        newsletter.headline = headline;
        newsletter.financialWordsJson = financialWordsJson;
        newsletter.issuesJson = issuesJson;
        newsletter.statsJson = statsJson;
        newsletter.status = NewsletterStatus.REVIEW;
        newsletter.generationType = generationType;
        newsletter.createdBy = createdBy;
        newsletter.createdAt = createdAt;
        return newsletter;
    }

    public Long getNewsletterId() {
        return newsletterId;
    }

    public void setNewsletterId(Long newsletterId) {
        this.newsletterId = newsletterId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getFinancialWordsJson() {
        return financialWordsJson;
    }

    public void setFinancialWordsJson(String financialWordsJson) {
        this.financialWordsJson = financialWordsJson;
    }

    public String getIssuesJson() {
        return issuesJson;
    }

    public void setIssuesJson(String issuesJson) {
        this.issuesJson = issuesJson;
    }

    public String getStatsJson() {
        return statsJson;
    }

    public void setStatsJson(String statsJson) {
        this.statsJson = statsJson;
    }

    public NewsletterStatus getStatus() {
        return status;
    }

    public void setStatus(NewsletterStatus status) {
        this.status = status;
    }

    public NewsletterGenerationType getGenerationType() {
        return generationType;
    }

    public void setGenerationType(NewsletterGenerationType generationType) {
        this.generationType = generationType;
    }

    public void publish(LocalDateTime publishedAt) {
        this.status = NewsletterStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void retire() {
        this.status = NewsletterStatus.RETIRED;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(long createdBy) {
        this.createdBy = createdBy;
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
}
