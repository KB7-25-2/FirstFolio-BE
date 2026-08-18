package org.firstfolio.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.news.dto.request.NewsCreateRequest;
import org.firstfolio.news.dto.response.FinancialNewsItemResponse;
import org.firstfolio.news.service.NewsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 뉴스 등록 API.
 *
 * <p>내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). 컨트롤러마다 반복하지 않는다.</p>
 *
 * <p>지금은 AI 쪽 수집·요약 파이프라인 없이, 수동으로 실행하는 스크립트가 이 API를
 * 한 건씩 호출하는 것까지만 지원한다. 스케줄링·자동 수집은 이 API의 범위가 아니다.</p>
 */
@RestController
@RequestMapping("/api/internal/news")
@Tag(name = "내부 뉴스", description = "내부 호출용 금융 뉴스 등록 API")
public class InternalNewsController {

    private final NewsService newsService;

    public InternalNewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @PostMapping
    @Operation(
            summary = "금융 뉴스 등록",
            description = "금융 뉴스 한 건을 등록합니다. published_at을 생략하면 현재 시각으로 채웁니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "금융 뉴스 등록 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.FinancialNewsItem.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_REQUEST - 필수 필드 누락"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    )
            }
    )
    public ApiResponse<FinancialNewsItemResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "등록할 금융 뉴스 한 건"
            )
            @RequestBody NewsCreateRequest request
    ) {
        return ApiResponse.of(newsService.createArticle(request));
    }
}
