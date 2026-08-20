package org.firstfolio.newsletter.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterGenerationType;
import org.firstfolio.newsletter.domain.NewsletterStatus;
import org.firstfolio.newsletter.dto.request.FinancialWordRequest;
import org.firstfolio.newsletter.dto.request.NewsletterCreateRequest;
import org.firstfolio.newsletter.dto.request.NewsletterIssueRequest;
import org.firstfolio.newsletter.dto.request.NewsletterSourceRequest;
import org.firstfolio.newsletter.dto.request.NewsletterStatRequest;
import org.firstfolio.newsletter.mapper.NewsletterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsletterCreateServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);

    private NewsletterMapper newsletterMapper;
    private Environment environment;
    private NewsletterCreateService service;

    @BeforeEach
    void setUp() {
        newsletterMapper = mock(NewsletterMapper.class);
        environment = mock(Environment.class);
        when(environment.getProperty("newsletter.batch.ai-created-by")).thenReturn("1");
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);
        service = new NewsletterCreateService(newsletterMapper, environment, clock);

        when(newsletterMapper.insert(any())).thenAnswer(invocation -> {
            Newsletter newsletter = invocation.getArgument(0);
            newsletter.setNewsletterId(1L);
            return 1;
        });
    }

    @Test
    void createsNewsletterInReviewStatus() {
        Newsletter created = service.create(validRequest());

        assertEquals(NewsletterStatus.REVIEW, created.getStatus());
        assertEquals(NewsletterGenerationType.AI, created.getGenerationType());
        assertEquals(1L, created.getNewsletterId());
        assertEquals(MONDAY, created.getWeekStartDate());

        ArgumentCaptor<Newsletter> captor = ArgumentCaptor.forClass(Newsletter.class);
        verify(newsletterMapper).insert(captor.capture());
        assertTrue(captor.getValue().getFinancialWordsJson().contains("요구불예금"));
    }

    @Test
    void rejectsWeekStartDateNotMonday() {
        NewsletterCreateRequest request = new NewsletterCreateRequest(
                LocalDate.of(2026, 8, 18),
                "대제목",
                validFinancialWords(),
                validIssues(),
                validStats()
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.create(request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void rejectsBlankHeadline() {
        NewsletterCreateRequest request = new NewsletterCreateRequest(
                MONDAY, "  ", validFinancialWords(), validIssues(), validStats()
        );

        assertThrows(ApiException.class, () -> service.create(request));
    }

    @Test
    void rejectsFinancialWordsWithWrongSize() {
        NewsletterCreateRequest request = new NewsletterCreateRequest(
                MONDAY,
                "대제목",
                List.of(new FinancialWordRequest("요구불예금", "정의")),
                validIssues(),
                validStats()
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.create(request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void rejectsIssueWithoutSources() {
        NewsletterCreateRequest request = new NewsletterCreateRequest(
                MONDAY,
                "대제목",
                validFinancialWords(),
                List.of(
                        new NewsletterIssueRequest("제목1", "요약1", "요구불예금", List.of()),
                        new NewsletterIssueRequest("제목2", "요약2", "경상수지", validSources()),
                        new NewsletterIssueRequest("제목3", "요약3", "해지환급금", validSources())
                ),
                validStats()
        );

        assertThrows(ApiException.class, () -> service.create(request));
    }

    @Test
    void rejectsStatsWithBlankValue() {
        NewsletterCreateRequest request = new NewsletterCreateRequest(
                MONDAY,
                "대제목",
                validFinancialWords(),
                validIssues(),
                List.of(
                        new NewsletterStatRequest("라벨1", ""),
                        new NewsletterStatRequest("라벨2", "값2"),
                        new NewsletterStatRequest("라벨3", "값3")
                )
        );

        assertThrows(ApiException.class, () -> service.create(request));
    }

    @Test
    void throwsWhenAiCreatedByPropertyMissing() {
        when(environment.getProperty("newsletter.batch.ai-created-by")).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.create(validRequest())
        );
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    private NewsletterCreateRequest validRequest() {
        return new NewsletterCreateRequest(
                MONDAY, "대제목", validFinancialWords(), validIssues(), validStats()
        );
    }

    private List<FinancialWordRequest> validFinancialWords() {
        return List.of(
                new FinancialWordRequest("요구불예금", "필요할 때 언제든 빼 쓸 수 있는 예금"),
                new FinancialWordRequest("경상수지", "나라가 외국과 주고받은 돈의 성적표"),
                new FinancialWordRequest("해지환급금", "보험을 중간에 깨면 돌려받는 돈")
        );
    }

    private List<NewsletterIssueRequest> validIssues() {
        return List.of(
                new NewsletterIssueRequest("이슈1", "요약1", "요구불예금", validSources()),
                new NewsletterIssueRequest("이슈2", "요약2", "경상수지", validSources()),
                new NewsletterIssueRequest("이슈3", "요약3", "해지환급금", validSources())
        );
    }

    private List<NewsletterSourceRequest> validSources() {
        return List.of(new NewsletterSourceRequest(1L, "1:0", "https://example.com", "근거 문장"));
    }

    private List<NewsletterStatRequest> validStats() {
        return List.of(
                new NewsletterStatRequest("라벨1", "값1"),
                new NewsletterStatRequest("라벨2", "값2"),
                new NewsletterStatRequest("라벨3", "값3")
        );
    }
}
