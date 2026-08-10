package org.firstfolio.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.user.domain.User;

import java.time.LocalDateTime;

@Schema(description = "내 공개 프로필과 서비스 상태")
public record UserProfileResponse(
        @Schema(description = "사용자 ID", example = "101") long userId,
        @Schema(description = "Firebase 계정 이메일", example = "student@example.com") String email,
        @Schema(description = "닉네임", example = "첫투자자") String nickname,
        @Schema(description = "사용자 권한", example = "USER") String roleCode,
        @Schema(description = "뉴스레터 수신 동의 여부", example = "true") boolean newsletterOptIn,
        @Schema(description = "서비스 포인트 잔액. 모의투자금과 별도", example = "1250") int pointBalance,
        @Schema(description = "가입 시각", example = "2026-08-07T09:00:00") LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getUserId(), user.getEmail(), user.getNickname(),
                user.getRoleCode().name(), user.isNewsletterOptIn(),
                user.getPointBalance(), user.getCreatedAt()
        );
    }
}
