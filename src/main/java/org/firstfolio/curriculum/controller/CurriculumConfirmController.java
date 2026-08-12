package org.firstfolio.curriculum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.curriculum.dto.request.CurriculumConfirmRequest;
import org.firstfolio.curriculum.dto.response.CurriculumConfirmResponse;
import org.firstfolio.curriculum.service.CurriculumConfirmService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curriculum/confirm")
@Tag(name = "개인 커리큘럼", description = "온보딩 개인 커리큘럼 API")
public class CurriculumConfirmController {

    private final CurriculumConfirmService curriculumConfirmService;

    public CurriculumConfirmController(
            CurriculumConfirmService curriculumConfirmService
    ) {
        this.curriculumConfirmService = curriculumConfirmService;
    }

    @PostMapping
    @Operation(
            summary = "개인 커리큘럼 확정",
            description = "선택한 ASSET 대단원을 검증하고 FOUNDATION과 함께 최종 커리큘럼으로 저장합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "개인 커리큘럼 확정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.CurriculumConfirm.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "LEVEL_TEST_REQUIRED 또는 CURRICULUM_ALREADY_CONFIRMED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "INVALID_CURRICULUM_SELECTION"
                    )
            }
    )
    public ApiResponse<CurriculumConfirmResponse> confirm(
            @CurrentUser AuthenticatedUser currentUser,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "확정할 ASSET 대단원 ID 목록. 빈 배열 허용"
            )
            @RequestBody(required = false) CurriculumConfirmRequest request
    ) {
        return ApiResponse.of(CurriculumConfirmResponse.from(
                curriculumConfirmService.confirm(
                        currentUser.userId(),
                        request == null ? null : request.mainChapterIds()
                )
        ));
    }
}
