package org.firstfolio.news.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.news.domain.NewsArticle;
import org.firstfolio.news.dto.request.NewsPatchRequest;
import org.firstfolio.news.mapper.NewsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 4, 0);

    private NewsMapper newsMapper;
    private NewsService service;

    @BeforeEach
    void setUp() {
        newsMapper = mock(NewsMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        service = new NewsService(newsMapper, clock);
    }

    @Test
    void updatesProvidedFieldsOnly() {
        NewsArticle stored = article();
        when(newsMapper.findById(1L)).thenReturn(stored);
        when(newsMapper.update(stored)).thenReturn(1);

        NewsPatchRequest request = new NewsPatchRequest();
        request.setTitle("수정 제목");
        request.setImageUrl(null);

        var result = service.updateArticle(1L, request);

        assertEquals("수정 제목", result.getTitle());
        assertEquals("기존 요약", result.getSummary());
        assertNull(result.getImageUrl());
        assertEquals(NOW, stored.getUpdatedAt());
        verify(newsMapper).update(stored);
    }

    @Test
    void rejectsEmptyPatch() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.updateArticle(1L, new NewsPatchRequest())
        );

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(newsMapper, never()).findById(1L);
    }

    @Test
    void rejectsMissingNewsOnUpdate() {
        when(newsMapper.findById(99L)).thenReturn(null);

        NewsPatchRequest request = new NewsPatchRequest();
        request.setTitle("수정 제목");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.updateArticle(99L, request)
        );

        assertEquals(ErrorCode.FINANCIAL_NEWS_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deletesExistingNews() {
        when(newsMapper.findById(1L)).thenReturn(article());
        when(newsMapper.deleteById(1L)).thenReturn(1);

        var result = service.deleteArticle(1L);

        assertEquals(1L, result.financialNewsId());
        verify(newsMapper).deleteById(1L);
    }

    @Test
    void rejectsMissingNewsOnDelete() {
        when(newsMapper.findById(99L)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.deleteArticle(99L)
        );

        assertEquals(ErrorCode.FINANCIAL_NEWS_NOT_FOUND, exception.getErrorCode());
        verify(newsMapper, never()).deleteById(99L);
    }

    @Test
    void rejectsBlankTitleOnPatch() {
        when(newsMapper.findById(1L)).thenReturn(article());

        NewsPatchRequest request = new NewsPatchRequest();
        request.setTitle("  ");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.updateArticle(1L, request)
        );

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(newsMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    private NewsArticle article() {
        NewsArticle article = new NewsArticle();
        article.setFinancialNewsId(1L);
        article.setTitle("기존 제목");
        article.setSummary("기존 요약");
        article.setImageUrl("https://example.com/thumb.png");
        article.setSourceName("경제일보");
        article.setSourceUrl("https://example.com/source-news");
        article.setSourcePublishedAt(LocalDateTime.of(2026, 8, 16, 9, 0));
        article.setPublishedAt(LocalDateTime.of(2026, 8, 17, 9, 0));
        article.setCreatedAt(LocalDateTime.of(2026, 8, 17, 9, 0));
        article.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 9, 0));
        return article;
    }
}
