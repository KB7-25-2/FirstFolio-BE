package org.firstfolio.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.quiz.dto.response.QuizAttemptStartResponse;
import org.firstfolio.quiz.service.QuizAttemptStartService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/sub-chapters/{subChapterId}/quiz-attempts")
@Tag(name = "학습 퀴즈", description = "사용자용 학습 퀴즈 응시 API")
public class QuizAttemptController {

    private final QuizAttemptStartService quizAttemptStartService;

    public QuizAttemptController(QuizAttemptStartService quizAttemptStartService) {
        this.quizAttemptStartService = quizAttemptStartService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "소단원 퀴즈 응시 시작",
            description = "학습을 완료한 콘텐츠 버전의 공개 문항으로 응시와 문항 스냅샷을 생성합니다. "
                    + "진행 중 응시가 있으면 저장된 문항과 순서를 복원하며 정답과 해설은 반환하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201",
                            description = "소단원 퀴즈 시작 또는 진행 중 응시 복원 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizAttemptStart.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "QUIZ_NOT_AVAILABLE - 학습 또는 문항 공개 조건 미충족"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "503",
                            description = "CONTENT_UNAVAILABLE - 콘텐츠 또는 스냅샷 조회 실패"
                    )
            }
    )
    public ApiResponse<QuizAttemptStartResponse> start(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "퀴즈를 시작할 소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId
    ) {
        return ApiResponse.of(QuizAttemptStartResponse.from(
                quizAttemptStartService.start(
                        currentUser.userId(),
                        subChapterId
                )
        ));
    }
}
