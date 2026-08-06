package org.firstfolio.user.controller;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.user.dto.request.UserProfilePatchRequest;
import org.firstfolio.user.dto.response.UserProfilePatchResponse;
import org.firstfolio.user.dto.response.UserProfileResponse;
import org.firstfolio.user.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMe(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(UserProfileResponse.from(
                userProfileService.get(currentUser.userId())
        ));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfilePatchResponse> patchMe(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestBody(required = false) UserProfilePatchRequest request
    ) {
        return ApiResponse.of(UserProfilePatchResponse.from(
                userProfileService.patch(currentUser.userId(), request)
        ));
    }
}
