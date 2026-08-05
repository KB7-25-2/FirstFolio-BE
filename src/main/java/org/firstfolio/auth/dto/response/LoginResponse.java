package org.firstfolio.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.auth.service.LoginResult;
import org.firstfolio.user.domain.UserRole;

public record LoginResponse(
        UserSummary user,
        @JsonProperty("onboarding_step") OnboardingStep onboardingStep
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

    public record UserSummary(
            @JsonProperty("user_id") long userId,
            String nickname,
            @JsonProperty("role_code") UserRole roleCode
    ) {
    }
}
