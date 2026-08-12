package org.firstfolio.curriculum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.curriculum.dto.request.CurriculumDraftEditRequest;
import org.firstfolio.curriculum.dto.response.CurriculumDraftEditResponse;
import org.firstfolio.curriculum.dto.response.CurriculumDraftResponse;
import org.firstfolio.curriculum.service.CurriculumDraftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curriculum/draft")
@Tag(name = "개인 커리큘럼", description = "온보딩 개인 커리큘럼 API")
public class CurriculumDraftController {

    private final CurriculumDraftService curriculumDraftService;

    public CurriculumDraftController(
            CurriculumDraftService curriculumDraftService
    ) {
        this.curriculumDraftService = curriculumDraftService;
    }

    @GetMapping
    @Operation(
            summary = "개인 커리큘럼 기본 초안 조회",
            description = "채점 완료된 레벨 테스트 결과로 FOUNDATION, 오답 추천과 추가 후보를 구성합니다. "
                    + "초안은 저장하지 않고 요청 시 기본 상태로 다시 구성합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "개인 커리큘럼 기본 초안 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.CurriculumDraft.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "LEVEL_TEST_REQUIRED"
                    )
            }
    )
    public ApiResponse<CurriculumDraftResponse> getDefaultDraft(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(CurriculumDraftResponse.from(
                curriculumDraftService.getDefaultDraft(currentUser.userId())
        ));
    }

    @PutMapping
    @Operation(
            summary = "개인 커리큘럼 초안 편집",
            description = "선택한 활성 ASSET 대단원과 요청 순서를 검증하고 FOUNDATION이 포함된 초안을 반환합니다. "
                    + "편집 결과는 DB에 저장하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "개인 커리큘럼 초안 검증 및 정규화 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.CurriculumDraftEdit.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "LEVEL_TEST_REQUIRED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "INVALID_CURRICULUM_SELECTION"
                    )
            }
    )
    public ApiResponse<CurriculumDraftEditResponse> editDraft(
            @CurrentUser AuthenticatedUser currentUser,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "선택한 ASSET 대단원 ID 목록. 빈 배열 허용"
            )
            @RequestBody(required = false) CurriculumDraftEditRequest request
    ) {
        return ApiResponse.of(CurriculumDraftEditResponse.from(
                curriculumDraftService.editDraft(
                        currentUser.userId(),
                        request == null ? null : request.mainChapterIds()
                )
        ));
    }
}
