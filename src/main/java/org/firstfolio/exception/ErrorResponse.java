package org.firstfolio.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            @JsonProperty("request_id") String requestId
    ) {
    }
}
