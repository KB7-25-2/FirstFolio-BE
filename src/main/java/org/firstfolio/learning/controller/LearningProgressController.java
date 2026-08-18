package org.firstfolio.learning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.learning.dto.request.LearningProgressUpdateRequest;
import org.firstfolio.learning.dto.response.LearningProgressResponse;
import org.firstfolio.learning.dto.response.LearningProgressUpdateResponse;
import org.firstfolio.learning.service.LearningProgressService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/sub-chapters/{subChapterId}/progress")
@Tag(name = "학습", description = "사용자용 학습 진행 API")
public class LearningProgressController {

    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @PutMapping
    @Operation(
            summary = "소단원 학습 진도 저장",
            description = "진도가 없으면 현재 공개 콘텐츠 버전으로 최초 생성하고, 있으면 마지막 페이지 또는 완료 상태를 갱신합니다. "
                    + "동일 요청과 최초 완료 이후 요청은 기존 상태를 변경하지 않습니다. "
                    + "이전 소단원 강좌와 퀴즈를 완료하기 전에는 다음 소단원 진도를 저장할 수 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "학습 진도 생성 또는 갱신 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LearningProgressUpdate.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "SUB_CHAPTER_LOCKED - 이전 소단원 강좌 또는 퀴즈 미완료"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "SUB_CHAPTER_NOT_FOUND 또는 CONTENT_NOT_PUBLISHED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "CONTENT_VERSION_MISMATCH"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "INVALID_PAGE_ID"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "503",
                            description = "CONTENT_UNAVAILABLE"
                    )
            }
    )
    public ApiResponse<LearningProgressUpdateResponse> save(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "저장할 소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "콘텐츠 버전, 마지막 페이지와 IN_PROGRESS/COMPLETED 상태"
            )
            @RequestBody LearningProgressUpdateRequest request
    ) {
        return ApiResponse.of(LearningProgressUpdateResponse.from(
                learningProgressService.save(
                        currentUser.userId(),
                        subChapterId,
                        request.toCommand()
                )
        ));
    }

    @GetMapping
    @Operation(
            summary = "소단원 학습 진행 상태 조회",
            description = "현재 사용자의 소단원 진도와 퀴즈 완료·이어풀기 상태를 조회합니다. "
                    + "저장된 진도가 없으면 현재 공개 콘텐츠 버전과 NOT_STARTED 상태를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "학습 진행 상태 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LearningProgress.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "SUB_CHAPTER_NOT_FOUND 또는 CONTENT_NOT_PUBLISHED"
                    )
            }
    )
    public ApiResponse<LearningProgressResponse> getStatus(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "조회할 소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId
    ) {
        return ApiResponse.of(LearningProgressResponse.from(
                learningProgressService.getStatus(currentUser.userId(), subChapterId)
        ));
    }
}
