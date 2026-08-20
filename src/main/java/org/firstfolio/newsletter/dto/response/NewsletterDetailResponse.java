package org.firstfolio.newsletter.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.newsletter.domain.Newsletter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record NewsletterDetailResponse(
        Long newsletterId,
        LocalDate weekStartDate,
        String headline,
        List<FinancialWordResponse> financialWordsJson,
        List<NewsletterIssueResponse> issuesJson,
        List<NewsletterStatResponse> statsJson,
        String status,
        String generationType,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {

    public static NewsletterDetailResponse from(Newsletter newsletter, ObjectMapper objectMapper) {
        return new NewsletterDetailResponse(
                newsletter.getNewsletterId(),
                newsletter.getWeekStartDate(),
                newsletter.getHeadline(),
                readList(objectMapper, newsletter.getFinancialWordsJson(), FinancialWordResponse[].class),
                readList(objectMapper, newsletter.getIssuesJson(), NewsletterIssueResponse[].class),
                readList(objectMapper, newsletter.getStatsJson(), NewsletterStatResponse[].class),
                newsletter.getStatus().name(),
                newsletter.getGenerationType().name(),
                newsletter.getPublishedAt(),
                newsletter.getCreatedAt()
        );
    }

    private static <T> List<T> readList(ObjectMapper objectMapper, String json, Class<T[]> arrayType) {
        try {
            T[] items = objectMapper.readValue(json, arrayType);
            return List.of(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 뉴스레터 JSON을 읽을 수 없습니다.", exception);
        }
    }
}
