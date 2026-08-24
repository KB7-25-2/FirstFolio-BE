package org.firstfolio.newsletter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.dto.request.NewsletterCreateRequest;
import org.firstfolio.newsletter.dto.response.NewsletterCreateResponse;
import org.firstfolio.newsletter.dto.response.NewsletterStatusResponse;
import org.firstfolio.newsletter.service.NewsletterCreateService;
import org.firstfolio.newsletter.service.NewsletterPublicationService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 내부 뉴스레터 등록·발행 API.
 *
 * <p>내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). AI 배치가 주 1회
 * 생성한 뉴스레터 한 건을 등록하는 용도라 퀴즈 배치처럼 여러 건을 묶지 않는다.</p>
 *
 * <p>발행은 로그인 세션이 없는 GitHub Actions 스크립트가 호출하므로,
 * 감사 로그의 행위자는 {@code newsletter.batch.ai-created-by}로 고정된
 * 시스템 계정을 쓴다({@link NewsletterCreateService}가 등록 시 쓰는 것과 동일한 계정).</p>
 */
@RestController
@RequestMapping("/api/internal/newsletters")
@Tag(name = "내부 뉴스레터", description = "내부 호출용 주간 뉴스레터 등록·발행 API")
public class InternalNewsletterController {

    private static final String AI_CREATED_BY_PROPERTY = "newsletter.batch.ai-created-by";

    private final NewsletterCreateService newsletterCreateService;
    private final NewsletterPublicationService newsletterPublicationService;
    private final Environment environment;

    public InternalNewsletterController(
            NewsletterCreateService newsletterCreateService,
            NewsletterPublicationService newsletterPublicationService,
            Environment environment
    ) {
        this.newsletterCreateService = newsletterCreateService;
        this.newsletterPublicationService = newsletterPublicationService;
        this.environment = environment;
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

    @PostMapping("/{newsletterId}/publish")
    @Operation(
            summary = "뉴스레터 내부 발행",
            description = "REVIEW 상태의 뉴스레터를 PUBLISHED로 전환하고 구독자 발송을 트리거합니다. "
                    + "관리자 로그인 없이 자동화 스크립트가 호출하는 용도입니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "뉴스레터 발행 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "NEWSLETTER_NOT_FOUND - 뉴스레터를 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "NEWSLETTER_NOT_PUBLISHABLE - REVIEW 상태가 아니어서 발행할 수 없음"
                    )
            }
    )
    public ApiResponse<NewsletterStatusResponse> publish(
            @Parameter(description = "발행할 뉴스레터 ID", example = "1", required = true)
            @PathVariable long newsletterId,
            HttpServletRequest servletRequest
    ) {
        Newsletter newsletter = newsletterPublicationService.publish(
                newsletterId,
                resolveSystemActorId(),
                RequestIdFilter.currentRequestId(servletRequest)
        );
        return ApiResponse.of(NewsletterStatusResponse.from(newsletter));
    }

    private long resolveSystemActorId() {
        String value = environment.getProperty(AI_CREATED_BY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    AI_CREATED_BY_PROPERTY + " 설정이 없어 뉴스레터를 발행할 수 없습니다."
            );
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    AI_CREATED_BY_PROPERTY + " 값이 올바른 사용자 ID가 아닙니다: " + value
            );
        }
    }
}
