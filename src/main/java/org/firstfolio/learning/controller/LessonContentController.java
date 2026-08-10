package org.firstfolio.learning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.learning.dto.response.LessonContentResponse;
import org.firstfolio.learning.service.LessonContentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/sub-chapters")
@Tag(name = "학습", description = "사용자용 학습 콘텐츠 조회 API")
public class LessonContentController {

    private final LessonContentQueryService lessonContentQueryService;

    public LessonContentController(
            LessonContentQueryService lessonContentQueryService
    ) {
        this.lessonContentQueryService = lessonContentQueryService;
    }

    @GetMapping("/{subChapterId}")
    @Operation(
            summary = "공개 소단원 강좌 조회",
            description = "활성 소단원에 연결된 현재 PUBLISHED 강좌 콘텐츠를 조회합니다. "
                    + "백엔드가 DB에 기록된 객체 키와 불변 버전 ID로 로컬 또는 S3 저장소를 읽어 JSON을 직접 반환하며, 저장소 식별자는 노출하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "학습 콘텐츠 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.LessonContent.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "SUB_CHAPTER_NOT_FOUND 또는 CONTENT_NOT_PUBLISHED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "503", description = "CONTENT_UNAVAILABLE - 저장소 객체 또는 콘텐츠 형식·스키마가 올바르지 않음"
                    )
            }
    )
    public ApiResponse<LessonContentResponse> getLessonContent(
            @Parameter(description = "조회할 소단원 ID", example = "101", required = true)
            @PathVariable long subChapterId
    ) {
        return ApiResponse.of(LessonContentResponse.from(
                lessonContentQueryService.getPublishedLesson(subChapterId)
        ));
    }
}
