package org.firstfolio.news.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 관리자 뉴스 부분 수정 요청 ({@code PATCH /api/admin/financial-news/{financialNewsId}}).
 *
 * <p>본문에 포함한 필드만 변경한다. {@code image_url}은 {@code null}로 보내 썸네일을 지울 수 있다.</p>
 */
@Schema(description = "금융 뉴스 부분 수정 요청. 전달한 필드만 변경")
public class NewsPatchRequest {

    @Schema(description = "제목")
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
    @Schema(description = "서비스 노출 시각")
    private LocalDateTime publishedAt;

    private boolean titleProvided;
    private boolean summaryProvided;
    private boolean imageUrlProvided;
    private boolean sourceNameProvided;
    private boolean sourceUrlProvided;
    private boolean sourcePublishedAtProvided;
    private boolean publishedAtProvided;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    @JsonSetter("summary")
    public void setSummary(String summary) {
        this.summary = summary;
        this.summaryProvided = true;
    }

    @JsonSetter("image_url")
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.imageUrlProvided = true;
    }

    @JsonSetter("source_name")
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
        this.sourceNameProvided = true;
    }

    @JsonSetter("source_url")
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        this.sourceUrlProvided = true;
    }

    @JsonSetter("source_published_at")
    public void setSourcePublishedAt(LocalDateTime sourcePublishedAt) {
        this.sourcePublishedAt = sourcePublishedAt;
        this.sourcePublishedAtProvided = true;
    }

    @JsonSetter("published_at")
    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
        this.publishedAtProvided = true;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDateTime getSourcePublishedAt() {
        return sourcePublishedAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public boolean titleProvided() {
        return titleProvided;
    }

    public boolean summaryProvided() {
        return summaryProvided;
    }

    public boolean imageUrlProvided() {
        return imageUrlProvided;
    }

    public boolean sourceNameProvided() {
        return sourceNameProvided;
    }

    public boolean sourceUrlProvided() {
        return sourceUrlProvided;
    }

    public boolean sourcePublishedAtProvided() {
        return sourcePublishedAtProvided;
    }

    public boolean publishedAtProvided() {
        return publishedAtProvided;
    }

    public boolean hasAnyField() {
        return titleProvided
                || summaryProvided
                || imageUrlProvided
                || sourceNameProvided
                || sourceUrlProvided
                || sourcePublishedAtProvided
                || publishedAtProvided;
    }
}
