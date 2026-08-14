package org.firstfolio.curriculum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.curriculum.dto.request.CurriculumUpdateRequest;
import org.firstfolio.curriculum.dto.response.CurriculumUpdateResponse;
import org.firstfolio.curriculum.service.CurriculumUpdateService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curriculum")
@Tag(name = "개인 커리큘럼", description = "온보딩 개인 커리큘럼 API")
public class CurriculumUpdateController {

    private final CurriculumUpdateService curriculumUpdateService;

    public CurriculumUpdateController(
            CurriculumUpdateService curriculumUpdateService
    ) {
        this.curriculumUpdateService = curriculumUpdateService;
    }

    @PutMapping
    @Operation(
            summary = "확정된 개인 커리큘럼 수정",
            description = "선택한 ASSET 대단원을 검증하고 FOUNDATION을 자동 포함해 기존 커리큘럼을 수정합니다. "
                    + "제외한 항목과 기존 학습 이력은 삭제하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "개인 커리큘럼 수정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.CurriculumUpdate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "CURRICULUM_NOT_FOUND"
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
    public ApiResponse<CurriculumUpdateResponse> update(
            @CurrentUser AuthenticatedUser currentUser,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "수정할 ASSET 대단원 ID 목록. 빈 배열 허용"
            )
            @RequestBody(required = false) CurriculumUpdateRequest request
    ) {
        return ApiResponse.of(CurriculumUpdateResponse.from(
                curriculumUpdateService.update(
                        currentUser.userId(),
                        request == null ? null : request.mainChapterIds()
                )
        ));
    }
}
