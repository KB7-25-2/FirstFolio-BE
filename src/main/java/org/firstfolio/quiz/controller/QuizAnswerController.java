package org.firstfolio.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.quiz.dto.request.QuizAnswerSubmitRequest;
import org.firstfolio.quiz.dto.response.QuizAnswerGradingResponse;
import org.firstfolio.quiz.service.QuizAnswerGradingService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/quiz-attempts/{attemptId}/answers/{questionId}")
@Tag(name = "학습 퀴즈", description = "사용자용 학습 퀴즈 응시 API")
public class QuizAnswerController {

    private final QuizAnswerGradingService gradingService;

    public QuizAnswerController(QuizAnswerGradingService gradingService) {
        this.gradingService = gradingService;
    }

    @PutMapping
    @Operation(
            summary = "퀴즈 문항별 답안 제출 및 즉시 채점",
            description = "응시에 고정된 문항 스냅샷으로 단일 답안을 채점하고 정답과 해설을 반환합니다. "
                    + "동일 답안 재요청은 응시 상태와 관계없이 기존 결과를 반환하며 답안 순서는 강제하지 않습니다. "
                    + "마지막 미응답 문항이면 응시 결과를 확정하고 최초 응시에 한해 정답 수 기반 포인트를 "
                    + "같은 트랜잭션에서 지급합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "문항 채점 또는 동일 답안 멱등 재조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizAnswerGrading.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "QUIZ_ATTEMPT_FORBIDDEN - 본인 소유 응시가 아님"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "QUIZ_ATTEMPT_NOT_FOUND 또는 QUESTION_NOT_IN_ATTEMPT"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "ATTEMPT_ALREADY_GRADED 또는 ANSWER_ALREADY_SUBMITTED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "INVALID_SELECTED_CHOICE - 선택지 형식 또는 범위 오류"
                    )
            }
    )
    public ApiResponse<QuizAnswerGradingResponse> grade(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "퀴즈 응시 ID", example = "3001", required = true)
            @PathVariable long attemptId,
            @Parameter(description = "응시에 포함된 문항 버전 ID", example = "1001", required = true)
            @PathVariable long questionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "선택한 단일 답안. 예: {\"answer\":{\"key\":\"B\"}}"
            )
            @RequestBody(required = false) QuizAnswerSubmitRequest request
    ) {
        return ApiResponse.of(QuizAnswerGradingResponse.from(
                gradingService.grade(
                        currentUser.userId(),
                        attemptId,
                        questionId,
                        request == null ? null : request.selectedKey()
                )
        ));
    }
}
