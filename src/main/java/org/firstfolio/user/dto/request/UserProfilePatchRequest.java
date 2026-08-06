package org.firstfolio.user.dto.request;

public record UserProfilePatchRequest(
        String nickname,
        Boolean newsletterOptIn
) {
}
