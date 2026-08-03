package org.firstfolio.common.response;

/**
 * API_DOCS.md의 공통 오류 응답 형식.
 *
 * <pre>
 * {"error": {"code": "...", "message": "...", "request_id": "req-..."}}
 * </pre>
 */
public final class ErrorResponse {

    private final Error error;

    private ErrorResponse(Error error) {
        this.error = error;
    }

    public static ErrorResponse of(
            String code,
            String message,
            String requestId
    ) {
        return new ErrorResponse(new Error(code, message, requestId));
    }

    public Error getError() {
        return error;
    }

    public static final class Error {

        private final String code;
        private final String message;
        private final String requestId;

        private Error(String code, String message, String requestId) {
            this.code = code;
            this.message = message;
            this.requestId = requestId;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getRequestId() {
            return requestId;
        }
    }
}
