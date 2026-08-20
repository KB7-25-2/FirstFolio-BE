package org.firstfolio.newsletter.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterGenerationType;
import org.firstfolio.newsletter.domain.NewsletterStatus;
import org.firstfolio.newsletter.dto.response.NewsletterDetailResponse;
import org.firstfolio.newsletter.dto.response.NewsletterListResponse;
import org.firstfolio.newsletter.mapper.NewsletterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsletterQueryServiceTest {

    private static final String FINANCIAL_WORDS_JSON =
            "[{\"term\":\"요구불예금\",\"definition\":\"필요할 때 언제든 빼 쓸 수 있는 예금\"},"
                    + "{\"term\":\"경상수지\",\"definition\":\"나라가 외국과 주고받은 돈의 성적표\"},"
                    + "{\"term\":\"해지환급금\",\"definition\":\"보험을 중간에 깨면 돌려받는 돈\"}]";
    private static final String ISSUES_JSON =
            "[{\"title\":\"이슈1\",\"summary\":\"요약1\",\"related_term\":\"요구불예금\","
                    + "\"sources\":[{\"document_id\":1,\"chunk_key\":\"1:0\","
                    + "\"source_url\":\"https://example.com\",\"evidence_text\":\"근거\"}]}]";
    private static final String STATS_JSON =
            "[{\"label\":\"라벨1\",\"value\":\"값1\"}]";

    private NewsletterMapper newsletterMapper;
    private NewsletterQueryService service;

    @BeforeEach
    void setUp() {
        newsletterMapper = mock(NewsletterMapper.class);
        service = new NewsletterQueryService(newsletterMapper);
    }

    @Test
    void findByStatusParsesStoredJsonIntoStructuredResponse() {
        when(newsletterMapper.findByStatus(NewsletterStatus.REVIEW))
                .thenReturn(List.of(sampleNewsletter(1L)));

        NewsletterListResponse response = service.findByStatus(NewsletterStatus.REVIEW);

        assertEquals(1, response.items().size());
        NewsletterDetailResponse item = response.items().get(0);
        assertEquals(1L, item.newsletterId());
        assertEquals("REVIEW", item.status());
        assertEquals(3, item.financialWordsJson().size());
        assertEquals("요구불예금", item.financialWordsJson().get(0).term());
        assertEquals(1, item.issuesJson().size());
        assertEquals("요구불예금", item.issuesJson().get(0).relatedTerm());
        assertEquals(1, item.issuesJson().get(0).sources().size());
        assertEquals(1, item.statsJson().size());
    }

    @Test
    void findByIdReturnsDetail() {
        when(newsletterMapper.findById(1L)).thenReturn(sampleNewsletter(1L));

        NewsletterDetailResponse response = service.findById(1L);

        assertEquals(1L, response.newsletterId());
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(newsletterMapper.findById(1L)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> service.findById(1L));
        assertEquals(ErrorCode.NEWSLETTER_NOT_FOUND, exception.getErrorCode());
    }

    private Newsletter sampleNewsletter(long newsletterId) {
        Newsletter newsletter = Newsletter.review(
                LocalDate.of(2026, 8, 17),
                "역대 최대 흑자 속에서도, 돈은 안전자산으로",
                FINANCIAL_WORDS_JSON,
                ISSUES_JSON,
                STATS_JSON,
                NewsletterGenerationType.AI,
                1L,
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );
        newsletter.setNewsletterId(newsletterId);
        return newsletter;
    }
}
