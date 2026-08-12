package org.firstfolio.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.quiz.dto.request.QuizQuestionCreateRequest;
import org.firstfolio.quiz.dto.request.QuizQuestionVersionCreateRequest;
import org.firstfolio.quiz.dto.response.QuizQuestionCreateResponse;
import org.firstfolio.quiz.service.QuizQuestionRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/quiz-questions")
@Tag(name = "관리자 퀴즈 문항", description = "관리자용 퀴즈 문항 등록·버전 관리 API")
public class AdminQuizQuestionController {

    private final QuizQuestionRegistrationService registrationService;

    public AdminQuizQuestionController(
            QuizQuestionRegistrationService registrationService
    ) {
        this.registrationService = registrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "신규 퀴즈 문항 등록",
            description = "JSON Schema와 단원 참조를 검증하고 새 논리 문항의 첫 버전을 "
                    + "version_no=1, generation_type=HUMAN, DRAFT 상태로 등록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "퀴즈 문항 생성 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizQuestionCreate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "QUESTION_KEY_CONFLICT - 이미 존재하는 논리 키"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "QUESTION_VALIDATION_FAILED - 문항 또는 단원 참조 검증 실패"
                    )
            }
    )
    public ApiResponse<QuizQuestionCreateResponse> createQuestion(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "퀴즈 문항 JSON Schema에 맞는 신규 문항"
            )
            @RequestBody(required = false) QuizQuestionCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(QuizQuestionCreateResponse.from(
                registrationService.createQuestion(
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PostMapping("/{questionId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "퀴즈 문항 새 버전 등록",
            description = "기존 문항의 논리 키·사용처·유형·생성 방식을 계승하고 증가한 "
                    + "version_no의 새 불변 DRAFT 행을 등록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "퀴즈 문항 새 버전 생성 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizQuestionCreate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "QUESTION_NOT_FOUND - 기준 문항을 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "QUESTION_VERSION_CONFLICT - 새 버전 번호 충돌"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "QUESTION_VALIDATION_FAILED - 새 버전 검증 실패"
                    )
            }
    )
    public ApiResponse<QuizQuestionCreateResponse> createVersion(
            @Parameter(description = "기준이 되는 기존 문항 버전 ID", example = "1201", required = true)
            @PathVariable long questionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "새 버전에 저장할 문항 내용"
            )
            @RequestBody(required = false) QuizQuestionVersionCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(QuizQuestionCreateResponse.from(
                registrationService.createVersion(
                        questionId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
