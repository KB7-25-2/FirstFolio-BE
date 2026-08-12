package org.firstfolio.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.quiz.dto.response.LevelTestSubmitResponse;
import org.firstfolio.quiz.service.LevelTestSubmitService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/level-tests/attempts/{attemptId}/submit")
@Tag(name = "레벨 테스트", description = "온보딩 레벨 테스트 API")
public class LevelTestSubmitController {

    private final LevelTestSubmitService submitService;

    public LevelTestSubmitController(LevelTestSubmitService submitService) {
        this.submitService = submitService;
    }

    @PostMapping
    @Operation(
            summary = "레벨 테스트 최종 제출 및 채점",
            description = "배정된 모든 문항에 답한 레벨 테스트를 명시적으로 최종 제출합니다. "
                    + "응시 시점의 문항 스냅샷으로 일괄 채점하며, 재요청은 최초 확정 결과를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "최종 제출 또는 최초 확정 결과 재조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LevelTestSubmit.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "QUIZ_ATTEMPT_FORBIDDEN"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "QUIZ_ATTEMPT_NOT_FOUND"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "REQUIRED_ANSWERS_MISSING 또는 ATTEMPT_ALREADY_GRADED"
                    )
            }
    )
    public ApiResponse<LevelTestSubmitResponse> submit(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "레벨 테스트 응시 ID", example = "2001", required = true)
            @PathVariable long attemptId
    ) {
        return ApiResponse.of(LevelTestSubmitResponse.from(
                submitService.submit(currentUser.userId(), attemptId)
        ));
    }
}
