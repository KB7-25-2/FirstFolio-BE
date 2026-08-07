package org.firstfolio.content.controller;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.content.dto.request.LessonContentUploadRequest;
import org.firstfolio.content.dto.response.ContentVersionCreateResponse;
import org.firstfolio.content.service.ContentVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/sub-chapters")
public class AdminContentVersionController {

    private final ContentVersionService contentVersionService;

    public AdminContentVersionController(
            ContentVersionService contentVersionService
    ) {
        this.contentVersionService = contentVersionService;
    }

    @PostMapping("/{subChapterId}/content-versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContentVersionCreateResponse> uploadLesson(
            @PathVariable long subChapterId,
            @RequestBody(required = false) LessonContentUploadRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(ContentVersionCreateResponse.from(
                contentVersionService.uploadLesson(
                        subChapterId,
                        request,
                        currentUser.userId(),
                        RequestIdFilter.currentRequestId(servletRequest)
                )
        ));
    }
}
