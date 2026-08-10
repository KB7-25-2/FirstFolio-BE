package org.firstfolio.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Firebase 인증이 완료된 사용자의 회원가입 정보")
public record SignupRequest(
        @Schema(description = "2~10자의 서비스 닉네임", example = "첫투자자")
        String nickname,
        @JsonProperty("required_terms_agreed")
        @Schema(description = "필수 약관 동의 여부. true만 허용", example = "true")
        Boolean requiredTermsAgreed
) {
}
