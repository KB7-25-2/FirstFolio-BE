package org.firstfolio.news.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 내부 뉴스 등록 요청 ({@code POST /api/internal/news}).
 *
 * <p>{@code publishedAt}을 비우면 서비스 노출 시각을 현재 시각으로 채운다. 원문 발행 시각
 * ({@code sourcePublishedAt})은 호출한 쪽이 반드시 채워야 한다.</p>
 */
@Schema(description = "금융 뉴스 등록 요청")
public class NewsCreateRequest {

    @Schema(description = "제목", example = "예·적금 금리 비교 수요 증가…은행권 경쟁 격화")
    private String title;
    @Schema(description = "요약")
    private String summary;
    @Schema(description = "썸네일 이미지 URL")
    private String imageUrl;
    @Schema(description = "원문 언론사명")
    private String sourceName;
    @Schema(description = "원문 기사 URL")
    private String sourceUrl;
    @Schema(description = "원문 기사 발행 시각")
    private LocalDateTime sourcePublishedAt;
    @Schema(description = "서비스 노출 시각. 생략하면 현재 시각")
    private LocalDateTime publishedAt;

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
}
