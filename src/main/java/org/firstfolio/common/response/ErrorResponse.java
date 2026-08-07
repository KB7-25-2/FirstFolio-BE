package org.firstfolio.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API_DOCS.md의 공통 오류 응답 형식.
 *
 * <pre>
 * {"error": {"code": "...", "message": "...", "request_id": "req-..."}}
 * </pre>
 */
@Schema(description = "FirstFolio API 공통 오류 응답")
public final class ErrorResponse {

    @Schema(description = "오류 상세")
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

    @Schema(description = "오류 코드, 사용자 메시지와 요청 추적 ID")
    public static final class Error {

        @Schema(description = "클라이언트가 분기 처리할 오류 코드", example = "INVALID_REQUEST")
        private final String code;
        @Schema(description = "오류 설명", example = "요청값을 확인해 주세요.")
        private final String message;
        @Schema(description = "서버 로그 추적용 요청 ID", example = "req-01J5FOLIO8N2X")
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
