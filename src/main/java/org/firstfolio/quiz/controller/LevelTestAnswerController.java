package org.firstfolio.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.quiz.dto.request.LevelTestAnswerSaveRequest;
import org.firstfolio.quiz.dto.response.LevelTestAnswerSaveResponse;
import org.firstfolio.quiz.service.LevelTestAnswerSaveService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/level-tests/attempts/{attemptId}/answers")
@Tag(name = "레벨 테스트", description = "온보딩 레벨 테스트 API")
public class LevelTestAnswerController {

    private final LevelTestAnswerSaveService answerSaveService;

    public LevelTestAnswerController(
            LevelTestAnswerSaveService answerSaveService
    ) {
        this.answerSaveService = answerSaveService;
    }

    @PutMapping
    @Operation(
            summary = "레벨 테스트 답안 저장",
            description = "진행 중인 레벨 테스트의 일부 또는 전체 답안을 저장하거나 변경합니다. "
                    + "이 단계에서는 채점하거나 응시를 최종 제출하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "답안 저장 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LevelTestAnswerSave.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "INVALID_REQUEST - 빈 답안 목록 또는 중복 문항"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "QUIZ_ATTEMPT_FORBIDDEN"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "QUIZ_ATTEMPT_NOT_FOUND 또는 QUESTION_NOT_IN_ATTEMPT"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "ATTEMPT_ALREADY_GRADED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "INVALID_SELECTED_CHOICE"
                    )
            }
    )
    public ApiResponse<LevelTestAnswerSaveResponse> save(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "레벨 테스트 응시 ID", example = "2001", required = true)
            @PathVariable long attemptId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "저장할 문항별 답안 목록"
            )
            @RequestBody(required = false) LevelTestAnswerSaveRequest request
    ) {
        return ApiResponse.of(LevelTestAnswerSaveResponse.from(
                answerSaveService.save(
                        currentUser.userId(),
                        attemptId,
                        request == null ? null : request.toCommands()
                )
        ));
    }
}
