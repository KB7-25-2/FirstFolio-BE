package org.firstfolio.learning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.learning.dto.response.PublicMainChapterListResponse;
import org.firstfolio.learning.dto.response.PublicSubChapterListResponse;
import org.firstfolio.learning.service.PublicChapterQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/main-chapters")
@Tag(name = "학습", description = "사용자용 학습 콘텐츠 조회 API")
public class PublicChapterController {

    private final PublicChapterQueryService publicChapterQueryService;

    public PublicChapterController(PublicChapterQueryService publicChapterQueryService) {
        this.publicChapterQueryService = publicChapterQueryService;
    }

    @GetMapping
    @Operation(
            summary = "공개 대단원 목록 조회",
            description = "활성 대단원을 표시 순서대로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공개 대단원 목록 조회 성공",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.PublicMainChapterList.class
                            )
                    )
            )
    )
    public ApiResponse<PublicMainChapterListResponse> getMainChapters() {
        return ApiResponse.of(PublicMainChapterListResponse.from(
                publicChapterQueryService.getMainChapters()
        ));
    }

    @GetMapping("/{mainChapterId}/sub-chapters")
    @Operation(
            summary = "공개 소단원 목록 조회",
            description = "활성 대단원 아래 활성 소단원을 표시 순서대로 조회하고 현재 공개 강좌 콘텐츠 존재 여부를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "공개 소단원 목록 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PublicSubChapterList.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "MAIN_CHAPTER_NOT_FOUND - 대단원이 없거나 비활성 상태"
                    )
            }
    )
    public ApiResponse<PublicSubChapterListResponse> getSubChapters(
            @Parameter(description = "조회할 대단원 ID", example = "2", required = true)
            @PathVariable long mainChapterId
    ) {
        return ApiResponse.of(PublicSubChapterListResponse.from(
                publicChapterQueryService.getSubChapters(mainChapterId)
        ));
    }
}
