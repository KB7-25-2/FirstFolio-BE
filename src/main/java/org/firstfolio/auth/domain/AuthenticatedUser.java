package org.firstfolio.auth.domain;

import org.firstfolio.user.domain.UserRole;

public record AuthenticatedUser(
        long userId,
        String firebaseUid,
        String nickname,
        UserRole roleCode
) {
}
