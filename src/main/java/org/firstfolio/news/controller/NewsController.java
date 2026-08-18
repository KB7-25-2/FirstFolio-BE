package org.firstfolio.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.news.dto.response.FinancialNewsListResponse;
import org.firstfolio.news.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-news")
@Tag(name = "뉴스", description = "금융 뉴스 스크랩 목록 조회 API")
public class NewsController {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 10;

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    @Operation(
            summary = "금융 뉴스 목록 조회",
            description = "발행 시각(published_at) 내림차순으로 최신 금융 뉴스를 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "금융 뉴스 목록 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.FinancialNews.class
                                    )
                            )
                    )
            }
    )
    public ApiResponse<FinancialNewsListResponse> getFinancialNews(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit
    ) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "limit은 1~10 사이의 정수여야 합니다.");
        }

        return ApiResponse.of(newsService.getFinancialNews(limit));
    }
}
