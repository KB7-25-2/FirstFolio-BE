package org.firstfolio.learning.controller;

import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.learning.dto.response.LessonContentResponse;
import org.firstfolio.learning.service.LessonContentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/sub-chapters")
public class LessonContentController {

    private final LessonContentQueryService lessonContentQueryService;

    public LessonContentController(
            LessonContentQueryService lessonContentQueryService
    ) {
        this.lessonContentQueryService = lessonContentQueryService;
    }

    @GetMapping("/{subChapterId}")
    public ApiResponse<LessonContentResponse> getLessonContent(
            @PathVariable long subChapterId
    ) {
        return ApiResponse.of(LessonContentResponse.from(
                lessonContentQueryService.getPublishedLesson(subChapterId)
        ));
    }
}
