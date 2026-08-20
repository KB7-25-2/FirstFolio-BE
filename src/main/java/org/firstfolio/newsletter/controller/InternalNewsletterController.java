package org.firstfolio.newsletter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.dto.request.NewsletterCreateRequest;
import org.firstfolio.newsletter.dto.response.NewsletterCreateResponse;
import org.firstfolio.newsletter.service.NewsletterCreateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 뉴스레터 등록 API.
 *
 * <p>내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). AI 배치가 주 1회
 * 생성한 뉴스레터 한 건을 등록하는 용도라 퀴즈 배치처럼 여러 건을 묶지 않는다.</p>
 */
@RestController
@RequestMapping("/api/internal/newsletters")
@Tag(name = "내부 뉴스레터", description = "내부 호출용 주간 뉴스레터 등록 API")
public class InternalNewsletterController {

    private final NewsletterCreateService newsletterCreateService;

    public InternalNewsletterController(NewsletterCreateService newsletterCreateService) {
        this.newsletterCreateService = newsletterCreateService;
    }

    @PostMapping
    @Operation(
            summary = "주간 뉴스레터 등록",
            description = "AI가 생성한 주간 뉴스레터 한 건을 등록합니다. status는 REVIEW로 저장됩니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "뉴스레터 등록 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_REQUEST - 필수 필드 누락 또는 형식 오류"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    )
            }
    )
    public ApiResponse<NewsletterCreateResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "등록할 주간 뉴스레터 한 건"
            )
            @RequestBody NewsletterCreateRequest request
    ) {
        Newsletter newsletter = newsletterCreateService.create(request);
        return ApiResponse.of(NewsletterCreateResponse.from(newsletter));
    }
}
