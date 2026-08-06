package org.firstfolio.user.dto.response;

import org.firstfolio.user.domain.User;

import java.time.LocalDateTime;

public record UserProfilePatchResponse(
        long userId,
        String nickname,
        boolean newsletterOptIn,
        LocalDateTime updatedAt
) {
    public static UserProfilePatchResponse from(User user) {
        return new UserProfilePatchResponse(
                user.getUserId(), user.getNickname(),
                user.isNewsletterOptIn(), user.getUpdatedAt()
        );
    }
}
