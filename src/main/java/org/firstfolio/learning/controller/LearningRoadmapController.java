package org.firstfolio.learning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.learning.dto.response.LearningRoadmapResponse;
import org.firstfolio.learning.service.LearningRoadmapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/roadmap")
@Tag(name = "학습", description = "사용자용 학습 콘텐츠 조회 API")
public class LearningRoadmapController {

    private final LearningRoadmapService roadmapService;

    public LearningRoadmapController(LearningRoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @GetMapping
    @Operation(
            summary = "학습 로드맵 통합 조회",
            description = "확정된 개인 커리큘럼의 대단원, 활성 소단원, "
                    + "사용자별 학습 진도와 대단원 퀴즈 상태를 한 번에 조회합니다. "
                    + "상세 강좌 JSON과 퀴즈 문항 본문은 포함하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "학습 로드맵 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LearningRoadmap.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "CURRICULUM_NOT_FOUND - 확정된 개인 커리큘럼이 없음"
                    )
            }
    )
    public ApiResponse<LearningRoadmapResponse> getRoadmap(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(roadmapService.getRoadmap(currentUser.userId()));
    }
}
