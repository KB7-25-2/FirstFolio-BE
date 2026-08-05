package org.firstfolio.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SignupRequest(
        String nickname,
        @JsonProperty("required_terms_agreed") Boolean requiredTermsAgreed
) {
}
