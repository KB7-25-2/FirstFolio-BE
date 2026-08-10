package org.firstfolio.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.auth.service.SignupResult;
import org.firstfolio.user.domain.UserRole;

@Schema(description = "회원가입 완료 정보")
public record SignupResponse(
        @JsonProperty("user_id") @Schema(description = "사용자 ID", example = "101") long userId,
        @Schema(description = "닉네임", example = "첫투자자") String nickname,
        @JsonProperty("role_code") @Schema(description = "사용자 권한", example = "USER") UserRole roleCode,
        @JsonProperty("onboarding_step")
        @Schema(description = "다음 온보딩 단계", example = "DIAGNOSIS_REQUIRED")
        OnboardingStep onboardingStep
) {
    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(
                result.userId(),
                result.nickname(),
                result.roleCode(),
                result.onboardingStep()
        );
    }
}
