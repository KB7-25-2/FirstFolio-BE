package org.firstfolio.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.auth.service.SignupResult;
import org.firstfolio.user.domain.UserRole;

public record SignupResponse(
        @JsonProperty("user_id") long userId,
        String nickname,
        @JsonProperty("role_code") UserRole roleCode,
        @JsonProperty("onboarding_step") OnboardingStep onboardingStep
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
