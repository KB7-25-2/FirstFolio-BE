package org.firstfolio.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.auth.service.LoginResult;
import org.firstfolio.user.domain.UserRole;

@Schema(description = "로그인한 사용자와 다음 온보딩 단계")
public record LoginResponse(
        @Schema(description = "로그인 사용자 요약") UserSummary user,
        @JsonProperty("onboarding_step")
        @Schema(description = "현재 상태에서 이어갈 온보딩 단계", example = "COMPLETED")
        OnboardingStep onboardingStep
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                new UserSummary(
                        result.userId(),
                        result.nickname(),
                        result.roleCode()
                ),
                result.onboardingStep()
        );
    }

    @Schema(description = "로그인 사용자 요약")
    public record UserSummary(
            @JsonProperty("user_id") @Schema(description = "사용자 ID", example = "101") long userId,
            @Schema(description = "닉네임", example = "첫투자자") String nickname,
            @JsonProperty("role_code") @Schema(description = "사용자 권한", example = "USER") UserRole roleCode
    ) {
    }
}
