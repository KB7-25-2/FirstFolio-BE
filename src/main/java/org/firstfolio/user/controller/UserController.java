package org.firstfolio.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
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
@Tag(name = "사용자", description = "현재 사용자의 공개 프로필과 수신 동의 관리 API")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    @Operation(
            summary = "내 프로필 조회",
            description = "로그인 사용자의 공개 프로필과 뉴스레터 수신 동의 상태를 조회합니다. "
                    + "포인트 잔액은 포인트 원장 또는 검증된 집계값을 기준으로 합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "프로필 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.UserProfile.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "UNAUTHORIZED - 인증 필요"
                    )
            }
    )
    public ApiResponse<UserProfileResponse> getMe(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(UserProfileResponse.from(
                userProfileService.get(currentUser.userId())
        ));
    }

    @PatchMapping("/me")
    @Operation(
            summary = "내 프로필 수정",
            description = "닉네임과 뉴스레터 수신 동의 중 전달된 필드만 수정합니다. "
                    + "뉴스레터 동의 상태가 바뀌면 동의·철회 이력을 함께 기록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "프로필 수정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.UserProfilePatch.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "NO_PATCH_FIELDS - 변경할 필드가 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "NICKNAME_CONFLICT - 이미 사용 중인 닉네임"
                    )
            }
    )
    public ApiResponse<UserProfilePatchResponse> patchMe(
            @CurrentUser AuthenticatedUser currentUser,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "변경할 닉네임 또는 뉴스레터 수신 동의 상태"
            )
            @RequestBody(required = false) UserProfilePatchRequest request
    ) {
        return ApiResponse.of(UserProfilePatchResponse.from(
                userProfileService.patch(currentUser.userId(), request)
        ));
    }
}
