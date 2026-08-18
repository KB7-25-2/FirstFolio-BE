package org.firstfolio.news.service;

import org.firstfolio.news.domain.NewsArticle;
import org.firstfolio.news.dto.response.FinancialNewsItemResponse;
import org.firstfolio.news.dto.response.FinancialNewsListResponse;
import org.firstfolio.news.mapper.NewsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 컨트롤러에 로직을 두지 않고 여기 모아, 대시보드 쪽(latest_news)에서도
 * 이 서비스를 그대로 재사용할 수 있게 한다.
 */
@Service
public class NewsService {

    private final NewsMapper newsMapper;

    public NewsService(NewsMapper newsMapper) {
        this.newsMapper = newsMapper;
    }

    public FinancialNewsListResponse getFinancialNews(int limit) {
        List<NewsArticle> articles = newsMapper.findLatest(limit);

        List<FinancialNewsItemResponse> items = articles.stream()
            .map(this::toItem)
            .toList();

        return new FinancialNewsListResponse(items);
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
