package org.firstfolio.auth.service;

import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.user.domain.UserRole;

public record SignupResult(
        long userId,
        String nickname,
        UserRole roleCode,
        OnboardingStep onboardingStep
) {
}
