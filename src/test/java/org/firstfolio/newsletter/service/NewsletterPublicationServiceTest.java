package org.firstfolio.newsletter.service;

import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterGenerationType;
import org.firstfolio.newsletter.domain.NewsletterStatus;
import org.firstfolio.newsletter.mapper.NewsletterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsletterPublicationServiceTest {

    private static final long ACTOR_ID = 900L;
    private static final String REQUEST_ID = "req-newsletter-publication";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 6, 0);

    private NewsletterMapper newsletterMapper;
    private AdminAuditLogMapper auditLogMapper;
    private NewsletterPublicationService service;

    @BeforeEach
    void setUp() {
        newsletterMapper = mock(NewsletterMapper.class);
        auditLogMapper = mock(AdminAuditLogMapper.class);
        service = new NewsletterPublicationService(
                newsletterMapper,
                auditLogMapper,
                Clock.fixed(Instant.parse("2026-08-20T06:00:00Z"), ZoneOffset.UTC)
        );

        when(auditLogMapper.insert(
                anyLong(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), any()
        )).thenReturn(1);
    }

    @Test
    void publishesReviewNewsletter() {
        Newsletter target = reviewNewsletter(1L);
        when(newsletterMapper.findByIdForUpdate(1L)).thenReturn(target);
        when(newsletterMapper.publishReview(1L, NOW)).thenReturn(1);

        Newsletter published = service.publish(1L, ACTOR_ID, REQUEST_ID);

        assertEquals(NewsletterStatus.PUBLISHED, published.getStatus());
        assertEquals(NOW, published.getPublishedAt());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("PUBLISH"), eq("NEWSLETTER"), eq(1L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
    }

    @Test
    void rejectsPublishWhenNotFound() {
        when(newsletterMapper.findByIdForUpdate(1L)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.publish(1L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.NEWSLETTER_NOT_FOUND, exception.getErrorCode());
        verify(newsletterMapper, never()).publishReview(anyLong(), any());
    }

    @Test
    void rejectsPublishWhenAlreadyPublished() {
        Newsletter target = reviewNewsletter(1L);
        target.publish(LocalDateTime.of(2026, 8, 19, 6, 0));
        when(newsletterMapper.findByIdForUpdate(1L)).thenReturn(target);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.publish(1L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.NEWSLETTER_NOT_PUBLISHABLE, exception.getErrorCode());
    }

    @Test
    void retiresPublishedNewsletter() {
        Newsletter target = reviewNewsletter(1L);
        target.publish(LocalDateTime.of(2026, 8, 19, 6, 0));
        when(newsletterMapper.findByIdForUpdate(1L)).thenReturn(target);
        when(newsletterMapper.retirePublished(1L)).thenReturn(1);

        Newsletter retired = service.retire(1L, ACTOR_ID, REQUEST_ID);

        assertEquals(NewsletterStatus.RETIRED, retired.getStatus());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("RETIRE"), eq("NEWSLETTER"), eq(1L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
    }

    @Test
    void rejectsRetireWhenNotPublished() {
        Newsletter target = reviewNewsletter(1L);
        when(newsletterMapper.findByIdForUpdate(1L)).thenReturn(target);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.retire(1L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.NEWSLETTER_NOT_RETIRABLE, exception.getErrorCode());
    }

    @Test
    void rejectsRetireWhenNotFound() {
        when(newsletterMapper.findByIdForUpdate(1L)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.retire(1L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.NEWSLETTER_NOT_FOUND, exception.getErrorCode());
    }

    private Newsletter reviewNewsletter(long newsletterId) {
        Newsletter newsletter = Newsletter.review(
                LocalDate.of(2026, 8, 17),
                "대제목",
                "[]",
                "[]",
                "[]",
                NewsletterGenerationType.AI,
                1L,
                LocalDateTime.of(2026, 8, 20, 5, 0)
        );
        newsletter.setNewsletterId(newsletterId);
        return newsletter;
    }
}
