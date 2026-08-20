package org.firstfolio.newsletter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.newsletter.domain.NewsletterStatus;
import org.firstfolio.newsletter.dto.response.NewsletterDetailResponse;
import org.firstfolio.newsletter.dto.response.NewsletterListResponse;
import org.firstfolio.newsletter.dto.response.NewsletterStatusResponse;
import org.firstfolio.newsletter.service.NewsletterPublicationService;
import org.firstfolio.newsletter.service.NewsletterQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/newsletters")
@Tag(name = "관리자 뉴스레터", description = "관리자용 주간 뉴스레터 검수·게시·비공개 API")
public class AdminNewsletterController {

    private final NewsletterQueryService queryService;
    private final NewsletterPublicationService publicationService;

    public AdminNewsletterController(
            NewsletterQueryService queryService,
            NewsletterPublicationService publicationService
    ) {
        this.queryService = queryService;
        this.publicationService = publicationService;
    }

    @GetMapping
    @Operation(
            summary = "뉴스레터 목록 조회",
            description = "상태별로 뉴스레터 목록을 최신순으로 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "뉴스레터 목록 조회 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_REQUEST - status 값이 올바르지 않음"
                    )
            }
    )
    public ApiResponse<NewsletterListResponse> findNewsletters(
            @Parameter(description = "조회할 상태", example = "REVIEW", required = true)
            @RequestParam("status") NewsletterStatus status
    ) {
        return ApiResponse.of(queryService.findByStatus(status));
    }

    @GetMapping("/{newsletterId}")
    @Operation(
            summary = "뉴스레터 상세 조회",
            description = "대제목·금융단어·이슈·숫자를 포함한 뉴스레터 전체 내용을 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "뉴스레터 상세 조회 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "NEWSLETTER_NOT_FOUND - 뉴스레터를 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<NewsletterDetailResponse> findNewsletter(
            @Parameter(description = "조회할 뉴스레터 ID", example = "1", required = true)
            @PathVariable long newsletterId
    ) {
        return ApiResponse.of(queryService.findById(newsletterId));
    }

    @PostMapping("/{newsletterId}/publish")
    @Operation(
            summary = "뉴스레터 게시",
            description = "REVIEW 상태의 뉴스레터를 PUBLISHED로 전환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "뉴스레터 게시 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "NEWSLETTER_NOT_FOUND - 뉴스레터를 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "NEWSLETTER_NOT_PUBLISHABLE - REVIEW 상태가 아니어서 게시할 수 없음"
                    )
            }
    )
    public ApiResponse<NewsletterStatusResponse> publishNewsletter(
            @Parameter(description = "게시할 뉴스레터 ID", example = "1", required = true)
            @PathVariable long newsletterId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(NewsletterStatusResponse.from(
                publicationService.publish(
                        newsletterId,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PostMapping("/{newsletterId}/retire")
    @Operation(
            summary = "뉴스레터 비공개 전환",
            description = "PUBLISHED 상태의 뉴스레터를 RETIRED로 전환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "뉴스레터 비공개 전환 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "NEWSLETTER_NOT_FOUND - 뉴스레터를 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "NEWSLETTER_NOT_RETIRABLE - 공개 상태가 아닌 뉴스레터"
                    )
            }
    )
    public ApiResponse<NewsletterStatusResponse> retireNewsletter(
            @Parameter(description = "비공개할 뉴스레터 ID", example = "1", required = true)
            @PathVariable long newsletterId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(NewsletterStatusResponse.from(
                publicationService.retire(
                        newsletterId,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
