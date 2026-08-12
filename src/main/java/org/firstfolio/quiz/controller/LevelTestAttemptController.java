package org.firstfolio.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.quiz.dto.response.LevelTestAttemptStartResponse;
import org.firstfolio.quiz.service.LevelTestAttemptStartService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/level-tests/attempts")
@Tag(name = "레벨 테스트", description = "온보딩 레벨 테스트 API")
public class LevelTestAttemptController {

    private final LevelTestAttemptStartService startService;

    public LevelTestAttemptController(LevelTestAttemptStartService startService) {
        this.startService = startService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "레벨 테스트 응시 시작",
            description = "활성 ASSET 대단원의 현재 공개 문항으로 통합 응시를 생성합니다. "
                    + "진행 중 응시가 있으면 배정 문항과 저장 답안을 복원합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201",
                            description = "레벨 테스트 시작 또는 진행 중 응시 복원 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LevelTestAttemptStart.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "LEVEL_TEST_ALREADY_COMPLETED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "LEVEL_TEST_QUESTION_SET_INVALID"
                    )
            }
    )
    public ApiResponse<LevelTestAttemptStartResponse> start(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(LevelTestAttemptStartResponse.from(
                startService.start(currentUser.userId())
        ));
    }
}
