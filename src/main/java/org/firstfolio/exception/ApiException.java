package org.firstfolio.exception;

/**
 * API_DOCS.md의 오류 코드로 응답해야 하는 예외.
 * 서비스 계층에서 {@code throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED)} 형태로 사용한다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
