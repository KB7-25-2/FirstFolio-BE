package org.firstfolio.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.user.domain.User;

import java.time.LocalDateTime;

@Schema(description = "프로필 수정 결과")
public record UserProfilePatchResponse(
        @Schema(description = "사용자 ID", example = "101") long userId,
        @Schema(description = "수정 후 닉네임", example = "장기투자자") String nickname,
        @Schema(description = "수정 후 뉴스레터 수신 동의 여부", example = "true") boolean newsletterOptIn,
        @Schema(description = "수정 시각", example = "2026-08-07T10:15:00") LocalDateTime updatedAt
) {
    public static UserProfilePatchResponse from(User user) {
        return new UserProfilePatchResponse(
                user.getUserId(), user.getNickname(),
                user.isNewsletterOptIn(), user.getUpdatedAt()
        );
    }
}
