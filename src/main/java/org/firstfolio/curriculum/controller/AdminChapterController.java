package org.firstfolio.curriculum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.dto.request.MainChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.MainChapterPatchRequest;
import org.firstfolio.curriculum.dto.request.SubChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.SubChapterPatchRequest;
import org.firstfolio.curriculum.dto.response.MainChapterCreateResponse;
import org.firstfolio.curriculum.dto.response.MainChapterListResponse;
import org.firstfolio.curriculum.dto.response.MainChapterPatchResponse;
import org.firstfolio.curriculum.dto.response.SubChapterCreateResponse;
import org.firstfolio.curriculum.dto.response.SubChapterListResponse;
import org.firstfolio.curriculum.dto.response.SubChapterPatchResponse;
import org.firstfolio.curriculum.service.ChapterMetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "관리자 학습 메타데이터", description = "관리자용 대단원·소단원 메타데이터 관리 API")
public class AdminChapterController {

    private final ChapterMetadataService chapterMetadataService;

    public AdminChapterController(ChapterMetadataService chapterMetadataService) {
        this.chapterMetadataService = chapterMetadataService;
    }

    @GetMapping("/main-chapters")
    @Operation(
            summary = "대단원 목록 조회",
            description = "FOUNDATION과 ASSET 대단원 메타데이터를 선택 조건으로 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "대단원 목록 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.MainChapterList.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    )
            }
    )
    public ApiResponse<MainChapterListResponse> getMainChapters(
            @Parameter(description = "대단원 구분 필터", example = "ASSET")
            @RequestParam(name = "chapter_type", required = false)
            ChapterType chapterType,
            @Parameter(description = "활성 여부 필터", example = "true")
            @RequestParam(name = "is_active", required = false)
            Boolean active
    ) {
        return ApiResponse.of(MainChapterListResponse.from(
                chapterMetadataService.getAllMainChapters(chapterType, active)
        ));
    }

    @PostMapping("/main-chapters")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "대단원 생성",
            description = "관리자가 대단원 메타데이터를 생성합니다. 활성 FOUNDATION은 하나만 허용하며, "
                    + "ASSET 자산군은 DEPOSIT_SAVINGS, BOND, STOCK, FUND만 허용합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "대단원 생성 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.MainChapterCreate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "FOUNDATION_CONFLICT - 활성 FOUNDATION이 이미 존재함"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "INVALID_MAIN_CHAPTER - 대단원 구분 또는 자산군이 올바르지 않음"
                    )
            }
    )
    public ApiResponse<MainChapterCreateResponse> createMainChapter(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "생성할 대단원 메타데이터"
            )
            @RequestBody(required = false) MainChapterCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(MainChapterCreateResponse.from(
                chapterMetadataService.createMainChapter(
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PatchMapping("/main-chapters/{mainChapterId}")
    @Operation(
            summary = "대단원 수정",
            description = "대단원명·설명·노출 순서·활성 상태 중 전달된 필드만 수정합니다. "
                    + "chapter_type과 asset_type은 변경할 수 없으며 변경 전후 값을 감사 로그에 남깁니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "대단원 수정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.MainChapterPatch.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "MAIN_CHAPTER_NOT_FOUND - 대단원을 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<MainChapterPatchResponse> patchMainChapter(
            @Parameter(description = "대단원 ID", example = "3", required = true)
            @PathVariable long mainChapterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "수정할 대단원 필드"
            )
            @RequestBody(required = false) MainChapterPatchRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(MainChapterPatchResponse.from(
                chapterMetadataService.patchMainChapter(
                        mainChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @GetMapping("/main-chapters/{mainChapterId}/sub-chapters")
    @Operation(
            summary = "소단원 목록 조회",
            description = "특정 대단원 아래 소단원 메타데이터와 각 소단원의 현재 공개 콘텐츠 버전 ID를 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "소단원 목록 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.SubChapterList.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "MAIN_CHAPTER_NOT_FOUND - 대단원을 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<SubChapterListResponse> getSubChapters(
            @Parameter(description = "대단원 ID", example = "2", required = true)
            @PathVariable long mainChapterId
    ) {
        return ApiResponse.of(SubChapterListResponse.from(
                chapterMetadataService.getAllSubChapters(mainChapterId)
        ));
    }

    @PostMapping("/main-chapters/{mainChapterId}/sub-chapters")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "소단원 생성",
            description = "대단원 아래 일반 소단원을 생성합니다. 상품 소개 유형이나 예제 상품 ID는 받지 않으며, "
                    + "같은 대단원 안에서 display_order를 중복할 수 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "소단원 생성 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.SubChapterCreate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "SUB_CHAPTER_ORDER_CONFLICT - 같은 순서의 소단원이 이미 존재함"
                    )
            }
    )
    public ApiResponse<SubChapterCreateResponse> createSubChapter(
            @Parameter(description = "대단원 ID", example = "2", required = true)
            @PathVariable long mainChapterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "생성할 소단원 메타데이터"
            )
            @RequestBody(required = false) SubChapterCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(SubChapterCreateResponse.from(
                chapterMetadataService.createSubChapter(
                        mainChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PatchMapping("/sub-chapters/{subChapterId}")
    @Operation(
            summary = "소단원 수정",
            description = "소단원명·설명·대단원 내 순서·활성 상태 중 전달된 필드만 수정합니다. "
                    + "기존 콘텐츠 버전과 학습 이력은 보존합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "소단원 수정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.SubChapterPatch.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "SUB_CHAPTER_NOT_FOUND - 소단원을 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<SubChapterPatchResponse> patchSubChapter(
            @Parameter(description = "소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "수정할 소단원 필드"
            )
            @RequestBody(required = false) SubChapterPatchRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(SubChapterPatchResponse.from(
                chapterMetadataService.patchSubChapter(
                        subChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
