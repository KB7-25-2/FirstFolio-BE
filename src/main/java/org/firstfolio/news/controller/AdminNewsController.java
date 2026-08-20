package org.firstfolio.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.news.dto.request.NewsPatchRequest;
import org.firstfolio.news.dto.response.FinancialNewsDeleteResponse;
import org.firstfolio.news.dto.response.FinancialNewsItemResponse;
import org.firstfolio.news.service.NewsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 금융 뉴스 수정·삭제 API.
 *
 * <p>ADMIN 권한 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/admin/**}). 컨트롤러마다 권한 검사를 반복하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/admin/financial-news")
@Tag(name = "관리자 뉴스", description = "관리자용 금융 뉴스 수정·삭제 API")
public class AdminNewsController {

    private final NewsService newsService;

    public AdminNewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @PatchMapping("/{financialNewsId}")
    @Operation(
            summary = "금융 뉴스 수정",
            description = "등록된 금융 뉴스 한 건의 전달한 필드만 수정합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "금융 뉴스 수정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.FinancialNewsItem.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_REQUEST - 수정 필드 없음 또는 값 오류"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "FINANCIAL_NEWS_NOT_FOUND - 금융 뉴스를 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<FinancialNewsItemResponse> update(
            @Parameter(description = "수정할 금융 뉴스 ID", example = "1", required = true)
            @PathVariable long financialNewsId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "변경할 필드만 포함한 금융 뉴스"
            )
            @RequestBody(required = false) NewsPatchRequest request
    ) {
        return ApiResponse.of(newsService.updateArticle(financialNewsId, request));
    }

    @DeleteMapping("/{financialNewsId}")
    @Operation(
            summary = "금융 뉴스 삭제",
            description = "등록된 금융 뉴스 한 건을 삭제합니다. 목록 조회에서 즉시 제외됩니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "금융 뉴스 삭제 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.FinancialNewsDelete.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "FINANCIAL_NEWS_NOT_FOUND - 금융 뉴스를 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<FinancialNewsDeleteResponse> delete(
            @Parameter(description = "삭제할 금융 뉴스 ID", example = "1", required = true)
            @PathVariable long financialNewsId
    ) {
        return ApiResponse.of(newsService.deleteArticle(financialNewsId));
    }
}
