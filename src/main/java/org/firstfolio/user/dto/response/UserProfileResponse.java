package org.firstfolio.user.dto.response;

import org.firstfolio.user.domain.User;

import java.time.LocalDateTime;

public record UserProfileResponse(
        long userId,
        String email,
        String nickname,
        String roleCode,
        boolean newsletterOptIn,
        int pointBalance,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getUserId(), user.getEmail(), user.getNickname(),
                user.getRoleCode().name(), user.isNewsletterOptIn(),
                user.getPointBalance(), user.getCreatedAt()
        );
    }
}
