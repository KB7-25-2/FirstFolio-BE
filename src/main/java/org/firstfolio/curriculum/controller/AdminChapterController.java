package org.firstfolio.curriculum.controller;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.dto.request.MainChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.MainChapterPatchRequest;
import org.firstfolio.curriculum.dto.request.SubChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.SubChapterPatchRequest;
import org.firstfolio.curriculum.dto.response.MainChapterCreateResponse;
import org.firstfolio.curriculum.dto.response.MainChapterListResponse;
import org.firstfolio.curriculum.dto.response.MainChapterPatchResponse;
import org.firstfolio.curriculum.dto.response.SubChapterCreateResponse;
import org.firstfolio.curriculum.dto.response.SubChapterListResponse;
import org.firstfolio.curriculum.dto.response.SubChapterPatchResponse;
import org.firstfolio.curriculum.service.ChapterMetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
public class AdminChapterController {

    private final ChapterMetadataService chapterMetadataService;

    public AdminChapterController(ChapterMetadataService chapterMetadataService) {
        this.chapterMetadataService = chapterMetadataService;
    }

    @GetMapping("/main-chapters")
    public ApiResponse<MainChapterListResponse> getMainChapters(
            @RequestParam(name = "chapter_type", required = false)
            ChapterType chapterType,
            @RequestParam(name = "is_active", required = false)
            Boolean active
    ) {
        return ApiResponse.of(MainChapterListResponse.from(
                chapterMetadataService.getAllMainChapters(chapterType, active)
        ));
    }

    @PostMapping("/main-chapters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MainChapterCreateResponse> createMainChapter(
            @RequestBody(required = false) MainChapterCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(MainChapterCreateResponse.from(
                chapterMetadataService.createMainChapter(
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PatchMapping("/main-chapters/{mainChapterId}")
    public ApiResponse<MainChapterPatchResponse> patchMainChapter(
            @PathVariable long mainChapterId,
            @RequestBody(required = false) MainChapterPatchRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(MainChapterPatchResponse.from(
                chapterMetadataService.patchMainChapter(
                        mainChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @GetMapping("/main-chapters/{mainChapterId}/sub-chapters")
    public ApiResponse<SubChapterListResponse> getSubChapters(
            @PathVariable long mainChapterId
    ) {
        return ApiResponse.of(SubChapterListResponse.from(
                chapterMetadataService.getAllSubChapters(mainChapterId)
        ));
    }

    @PostMapping("/main-chapters/{mainChapterId}/sub-chapters")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubChapterCreateResponse> createSubChapter(
            @PathVariable long mainChapterId,
            @RequestBody(required = false) SubChapterCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(SubChapterCreateResponse.from(
                chapterMetadataService.createSubChapter(
                        mainChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }

    @PatchMapping("/sub-chapters/{subChapterId}")
    public ApiResponse<SubChapterPatchResponse> patchSubChapter(
            @PathVariable long subChapterId,
            @RequestBody(required = false) SubChapterPatchRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(SubChapterPatchResponse.from(
                chapterMetadataService.patchSubChapter(
                        subChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
