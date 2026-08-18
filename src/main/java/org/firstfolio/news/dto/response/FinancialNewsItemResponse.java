package org.firstfolio.news.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "금융 뉴스 한 건")
public final class FinancialNewsItemResponse {

    @Schema(description = "금융 뉴스 식별자")
    private final Long financialNewsId;
    @Schema(description = "제목")
    private final String title;
    @Schema(description = "요약")
    private final String summary;
    @Schema(description = "썸네일 이미지 URL")
    private final String imageUrl;
    @Schema(description = "원문 언론사명")
    private final String sourceName;
    @Schema(description = "원문 기사 URL")
    private final String sourceUrl;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "원문 기사 발행 시각")
    private final LocalDateTime sourcePublishedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "서비스 노출 시각")
    private final LocalDateTime publishedAt;

    public FinancialNewsItemResponse(
        Long financialNewsId,
        String title,
        String summary,
        String imageUrl,
        String sourceName,
        String sourceUrl,
        LocalDateTime sourcePublishedAt,
        LocalDateTime publishedAt
    ) {
        this.financialNewsId = financialNewsId;
        this.title = title;
        this.summary = summary;
        this.imageUrl = imageUrl;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.sourcePublishedAt = sourcePublishedAt;
        this.publishedAt = publishedAt;
    }

    public Long getFinancialNewsId() {
        return financialNewsId;
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
}
