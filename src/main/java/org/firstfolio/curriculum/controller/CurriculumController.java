package org.firstfolio.curriculum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.curriculum.dto.response.CurriculumOverviewResponse;
import org.firstfolio.curriculum.service.UserCurriculumQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curriculum")
@Tag(name = "개인 커리큘럼", description = "온보딩 개인 커리큘럼 API")
public class CurriculumController {

    private final UserCurriculumQueryService userCurriculumQueryService;

    public CurriculumController(
            UserCurriculumQueryService userCurriculumQueryService
    ) {
        this.userCurriculumQueryService = userCurriculumQueryService;
    }

    @GetMapping
    @Operation(
            summary = "확정된 개인 커리큘럼 조회",
            description = "확정된 커리큘럼을 표시 순서대로 대단원별 활성 소단원 완료율과 함께 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "개인 커리큘럼 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.CurriculumOverview.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "CURRICULUM_NOT_FOUND"
                    )
            }
    )
    public ApiResponse<CurriculumOverviewResponse> getCurriculum(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(CurriculumOverviewResponse.from(
                userCurriculumQueryService.findOverview(currentUser.userId())
        ));
    }
}
