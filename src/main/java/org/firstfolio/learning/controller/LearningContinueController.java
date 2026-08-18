package org.firstfolio.learning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.learning.dto.response.LearningContinueResponse;
import org.firstfolio.learning.service.LearningContinueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/continue")
@Tag(name = "학습", description = "사용자용 학습 진행 API")
public class LearningContinueController {

    private final LearningContinueService learningContinueService;

    public LearningContinueController(
            LearningContinueService learningContinueService
    ) {
        this.learningContinueService = learningContinueService;
    }

    @GetMapping
    @Operation(
            summary = "학습 이어하기 위치 조회",
            description = "현재 사용자의 가장 최근 IN_PROGRESS 소단원과 마지막 페이지를 우선 반환합니다. "
                    + "진행 중인 강좌가 없으면 강좌를 마쳤지만 퀴즈를 완료하지 않은 가장 앞 소단원의 퀴즈를 반환하고, "
                    + "그 대상도 없으면 모든 활성 소단원을 최종 완료한 미완료 대단원의 퀴즈 진행·재도전 경로를 반환합니다. "
                    + "저장된 콘텐츠 버전이 현재 공개 버전과 다르면 안전한 호환 정책 확정 전까지 강좌를 조회하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "학습 이어하기 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LearningContinue.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "CONTINUE_POSITION_NOT_FOUND - 이어갈 미완료 학습 위치 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "503",
                            description = "CONTENT_UNAVAILABLE - 비활성 단원, 버전 불일치 또는 저장소 조회 실패"
                    )
            }
    )
    public ApiResponse<LearningContinueResponse> getContinuePosition(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(LearningContinueResponse.from(
                learningContinueService.getContinuePosition(
                        currentUser.userId()
                )
        ));
    }
}
