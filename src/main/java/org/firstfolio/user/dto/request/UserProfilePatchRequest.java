package org.firstfolio.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 프로필 부분 수정. 전달한 필드만 변경")
public record UserProfilePatchRequest(
        @Schema(description = "변경할 2~10자 닉네임", example = "장기투자자") String nickname,
        @Schema(description = "뉴스레터 수신 동의 여부", example = "true") Boolean newsletterOptIn
) {
}
