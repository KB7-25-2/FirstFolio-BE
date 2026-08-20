package org.firstfolio.news.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.news.domain.NewsArticle;
import org.firstfolio.news.dto.request.NewsCreateRequest;
import org.firstfolio.news.dto.request.NewsPatchRequest;
import org.firstfolio.news.dto.response.FinancialNewsDeleteResponse;
import org.firstfolio.news.dto.response.FinancialNewsItemResponse;
import org.firstfolio.news.dto.response.FinancialNewsListResponse;
import org.firstfolio.news.mapper.NewsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 컨트롤러에 로직을 두지 않고 여기 모아, 대시보드 쪽(latest_news)에서도
 * 이 서비스를 그대로 재사용할 수 있게 한다.
 */
@Service
public class NewsService {

    private final NewsMapper newsMapper;
    private final Clock clock;

    public NewsService(NewsMapper newsMapper, Clock clock) {
        this.newsMapper = newsMapper;
        this.clock = clock;
    }

    public FinancialNewsListResponse getFinancialNews(int limit) {
        List<NewsArticle> articles = newsMapper.findLatest(limit);

        List<FinancialNewsItemResponse> items = articles.stream()
            .map(this::toItem)
            .toList();

        return new FinancialNewsListResponse(items);
    }

    public FinancialNewsItemResponse createArticle(NewsCreateRequest request) {
        validate(request);

        LocalDateTime now = LocalDateTime.now(clock);
        NewsArticle article = new NewsArticle();
        article.setTitle(request.getTitle());
        article.setSummary(request.getSummary());
        article.setImageUrl(request.getImageUrl());
        article.setSourceName(request.getSourceName());
        article.setSourceUrl(request.getSourceUrl());
        article.setSourcePublishedAt(request.getSourcePublishedAt());
        article.setPublishedAt(request.getPublishedAt() != null ? request.getPublishedAt() : now);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);

        if (newsMapper.insert(article) != 1 || article.getFinancialNewsId() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        return toItem(article);
    }

    @Transactional
    public FinancialNewsItemResponse updateArticle(long financialNewsId, NewsPatchRequest request) {
        if (request == null || !request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 뉴스 필드가 없습니다.");
        }

        NewsArticle article = requireArticle(financialNewsId);
        applyPatch(article, request);
        article.setUpdatedAt(LocalDateTime.now(clock));

        if (newsMapper.update(article) != 1) {
            throw new ApiException(ErrorCode.FINANCIAL_NEWS_NOT_FOUND);
        }

        return toItem(article);
    }

    @Transactional
    public FinancialNewsDeleteResponse deleteArticle(long financialNewsId) {
        requireArticle(financialNewsId);

        if (newsMapper.deleteById(financialNewsId) != 1) {
            throw new ApiException(ErrorCode.FINANCIAL_NEWS_NOT_FOUND);
        }

        return new FinancialNewsDeleteResponse(financialNewsId);
    }

    private NewsArticle requireArticle(long financialNewsId) {
        NewsArticle article = newsMapper.findById(financialNewsId);
        if (article == null) {
            throw new ApiException(ErrorCode.FINANCIAL_NEWS_NOT_FOUND);
        }
        return article;
    }

    private void applyPatch(NewsArticle article, NewsPatchRequest request) {
        if (request.titleProvided()) {
            article.setTitle(requireText(request.getTitle(), "title"));
        }
        if (request.summaryProvided()) {
            article.setSummary(requireText(request.getSummary(), "summary"));
        }
        if (request.imageUrlProvided()) {
            article.setImageUrl(normalizeOptional(request.getImageUrl()));
        }
        if (request.sourceNameProvided()) {
            article.setSourceName(requireText(request.getSourceName(), "source_name"));
        }
        if (request.sourceUrlProvided()) {
            article.setSourceUrl(requireText(request.getSourceUrl(), "source_url"));
        }
        if (request.sourcePublishedAtProvided()) {
            if (request.getSourcePublishedAt() == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "source_published_at는 필수입니다.");
            }
            article.setSourcePublishedAt(request.getSourcePublishedAt());
        }
        if (request.publishedAtProvided()) {
            if (request.getPublishedAt() == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "published_at는 필수입니다.");
            }
            article.setPublishedAt(request.getPublishedAt());
        }
    }

    private String requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, fieldName + "은(는) 비어 있을 수 없습니다.");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validate(NewsCreateRequest request) {
        if (isBlank(request.getTitle())
                || isBlank(request.getSummary())
                || isBlank(request.getSourceName())
                || isBlank(request.getSourceUrl())
                || request.getSourcePublishedAt() == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "title, summary, source_name, source_url, source_published_at는 필수입니다."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private FinancialNewsItemResponse toItem(NewsArticle article) {
        return new FinancialNewsItemResponse(
            article.getFinancialNewsId(),
            article.getTitle(),
            article.getSummary(),
            article.getImageUrl(),
            article.getSourceName(),
            article.getSourceUrl(),
            article.getSourcePublishedAt(),
            article.getPublishedAt()
        );
    }
}
