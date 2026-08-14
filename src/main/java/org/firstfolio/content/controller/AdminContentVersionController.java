package org.firstfolio.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.content.dto.request.LessonContentUploadRequest;
import org.firstfolio.content.dto.response.ContentVersionCreateResponse;
import org.firstfolio.content.dto.response.ContentVersionListResponse;
import org.firstfolio.content.dto.response.ContentVersionPublishResponse;
import org.firstfolio.content.dto.response.ContentVersionRetireResponse;
import org.firstfolio.content.service.ContentVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "관리자 학습 콘텐츠", description = "관리자용 강좌 콘텐츠 버전 업로드·조회·게시·비공개 API")
public class AdminContentVersionController {

    private final ContentVersionService contentVersionService;

    public AdminContentVersionController(
            ContentVersionService contentVersionService
    ) {
        this.contentVersionService = contentVersionService;
    }

    @GetMapping("/sub-chapters/{subChapterId}/content-versions")
    @Operation(
            summary = "콘텐츠 버전 목록 조회",
            description = "특정 소단원의 콘텐츠 버전을 version_no 내림차순으로 조회합니다. "
                    + "현재 공개 버전은 current=true로 표시하며 저장소 객체 키와 버전 ID는 노출하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "콘텐츠 버전 목록 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ContentVersionList.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "SUB_CHAPTER_NOT_FOUND - 소단원을 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<ContentVersionListResponse> getContentVersions(
            @Parameter(description = "콘텐츠 버전을 조회할 소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId
    ) {
        return ApiResponse.of(ContentVersionListResponse.from(
                contentVersionService.getContentVersions(subChapterId)
        ));
    }

    @PostMapping("/sub-chapters/{subChapterId}/content-versions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "강좌 콘텐츠 버전 등록",
            description = "강좌 JSON 구조와 공개 퀴즈 문항 참조를 검증한 뒤 로컬 또는 S3 저장소에 새 불변 버전으로 업로드하고 DRAFT 메타데이터를 등록합니다. "
                    + "객체 키와 저장소 버전 ID는 서버가 생성합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "콘텐츠 버전 업로드 및 등록 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ContentVersionCreate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_REQUEST - 요청 형식이 올바르지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "SUB_CHAPTER_NOT_FOUND - 소단원을 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "CONTENT_VERSION_CONFLICT - 같은 버전 번호가 이미 존재함"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "CONTENT_VALIDATION_FAILED - JSON Schema 또는 퀴즈 참조 검증 실패"
                    )
            }
    )
    public ApiResponse<ContentVersionCreateResponse> uploadLesson(
            @Parameter(description = "강좌 콘텐츠를 등록할 소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "버전 번호와 JSON Schema에 맞는 소단원 강좌 JSON"
            )
            @RequestBody(required = false) LessonContentUploadRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(ContentVersionCreateResponse.from(
                contentVersionService.uploadLesson(
                        subChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PostMapping("/content-versions/{contentVersionId}/publish")
    @Operation(
            summary = "강좌 콘텐츠 버전 게시",
            description = "검증을 통과한 DRAFT 버전을 PUBLISHED로 전환하고 소단원의 현재 공개 버전으로 연결합니다. "
                    + "이전 공개 버전은 RETIRED 상태로 보존하며 상태 전환과 현재 버전 연결은 하나의 트랜잭션으로 처리합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "콘텐츠 버전 게시 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ContentVersionPublish.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "CONTENT_VERSION_NOT_FOUND - 콘텐츠 버전을 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "CONTENT_NOT_PUBLISHABLE - 게시 가능한 상태가 아님"
                    )
            }
    )
    public ApiResponse<ContentVersionPublishResponse> publishContentVersion(
            @Parameter(description = "게시할 콘텐츠 버전 ID", example = "302", required = true)
            @PathVariable long contentVersionId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(ContentVersionPublishResponse.from(
                contentVersionService.publishContentVersion(
                        contentVersionId,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PostMapping("/content-versions/{contentVersionId}/retire")
    @Operation(
            summary = "강좌 콘텐츠 버전 수동 비공개",
            description = "현재 공개 중인 PUBLISHED 버전을 RETIRED로 전환하고 소단원의 현재 공개 버전 연결을 해제합니다. "
                    + "저장소 객체와 과거 학습 이력은 삭제하지 않으며 상태 전환, 연결 해제와 감사 이력을 하나의 트랜잭션으로 처리합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "콘텐츠 버전 비공개 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ContentVersionRetire.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "CONTENT_VERSION_NOT_FOUND - 콘텐츠 버전을 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "CONTENT_NOT_RETIRABLE - 현재 공개 중인 버전이 아님"
                    )
            }
    )
    public ApiResponse<ContentVersionRetireResponse> retireContentVersion(
            @Parameter(description = "비공개할 콘텐츠 버전 ID", example = "301", required = true)
            @PathVariable long contentVersionId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(ContentVersionRetireResponse.from(
                contentVersionService.retireContentVersion(
                        contentVersionId,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
