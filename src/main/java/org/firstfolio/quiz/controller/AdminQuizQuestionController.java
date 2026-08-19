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
import org.firstfolio.quiz.dto.response.QuizQuestionPageResponse;
import org.firstfolio.quiz.dto.response.QuizQuestionStatusResponse;
import org.firstfolio.quiz.service.QuizQuestionPublicationService;
import org.firstfolio.quiz.service.QuizQuestionQueryService;
import org.firstfolio.quiz.service.QuizQuestionRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/quiz-questions")
@Tag(name = "관리자 퀴즈 문항", description = "관리자용 퀴즈 문항 조회·등록·버전 관리 API")
public class AdminQuizQuestionController {

    private final QuizQuestionRegistrationService registrationService;
    private final QuizQuestionPublicationService publicationService;
    private final QuizQuestionQueryService queryService;

    public AdminQuizQuestionController(
            QuizQuestionRegistrationService registrationService,
            QuizQuestionPublicationService publicationService,
            QuizQuestionQueryService queryService
    ) {
        this.registrationService = registrationService;
        this.publicationService = publicationService;
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "퀴즈 문항 목록 조회",
            description = "관리자가 퀴즈 문항을 사용처·단원·상태·논리 키로 검색합니다. "
                    + "결과는 question_id 오름차순 커서 페이지로 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "퀴즈 문항 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizQuestionPage.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    )
            }
    )
    public ApiResponse<QuizQuestionPageResponse> findQuestions(
            @Parameter(description = "문항 사용처", example = "SUB_CHAPTER")
            @RequestParam(value = "usage_type", required = false) String usageType,
            @Parameter(description = "대단원 필터", example = "2")
            @RequestParam(value = "main_chapter_id", required = false) Long mainChapterId,
            @Parameter(description = "소단원 필터", example = "101")
            @RequestParam(value = "sub_chapter_id", required = false) Long subChapterId,
            @Parameter(description = "버전 상태", example = "PUBLISHED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "논리 문항 키", example = "deposit-q-001")
            @RequestParam(value = "question_key", required = false) String questionKey,
            @Parameter(description = "다음 페이지 조회용 커서")
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return ApiResponse.of(queryService.findPage(
                usageType,
                mainChapterId,
                subChapterId,
                status,
                questionKey,
                cursor
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "신규 퀴즈 문항 등록",
            description = "JSON Schema와 단원 참조를 검증하고 새 논리 문항의 첫 버전을 "
                    + "version_no=1, generation_type=HUMAN, DRAFT 상태로 등록합니다. "
                    + "LEVEL_TEST와 MAIN_CHAPTER 문항은 양수 display_order가 필수입니다.",
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

    @PostMapping("/{questionId}/publish")
    @Operation(
            summary = "퀴즈 문항 버전 공개",
            description = "최신 DRAFT 또는 REVIEW 문항 버전을 PUBLISHED로 전환합니다. 같은 논리 키의 기존 공개 버전은 RETIRED로 보존하고 모든 상태 변경을 하나의 트랜잭션으로 처리합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "퀴즈 문항 공개 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizQuestionStatus.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "QUESTION_NOT_FOUND - 문항을 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "QUESTION_NOT_PUBLISHABLE - 최신 DRAFT/REVIEW가 아니거나 공개할 수 없는 상태"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "QUESTION_VALIDATION_FAILED - 문항 또는 단원 참조 재검증 실패"
                    )
            }
    )
    public ApiResponse<QuizQuestionStatusResponse> publishQuestion(
            @Parameter(description = "공개할 문항 버전 ID", example = "1201", required = true)
            @PathVariable long questionId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(QuizQuestionStatusResponse.from(
                publicationService.publish(
                        questionId,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PostMapping("/{questionId}/retire")
    @Operation(
            summary = "퀴즈 문항 버전 폐기",
            description = "PUBLISHED 문항을 RETIRED로 전환해 신규 퀴즈 배정과 새 강좌 참조 대상에서 제외합니다. 기존 강좌와 응시가 고정 참조한 문항 이력은 보존합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "퀴즈 문항 폐기 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.QuizQuestionStatus.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "QUESTION_NOT_FOUND - 문항을 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "QUESTION_NOT_RETIRABLE - 공개 상태가 아닌 문항"
                    )
            }
    )
    public ApiResponse<QuizQuestionStatusResponse> retireQuestion(
            @Parameter(description = "폐기할 문항 버전 ID", example = "1201", required = true)
            @PathVariable long questionId,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(QuizQuestionStatusResponse.from(
                publicationService.retire(
                        questionId,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
