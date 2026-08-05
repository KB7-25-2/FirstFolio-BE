package org.firstfolio.exception;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.response.ErrorResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;

/**
 * 모든 오류 응답을 API_DOCS.md의 {@code {"error": {...}}} 형식으로 통일한다.
 */
@RestControllerAdvice
public class CommonExceptionAdvice {

    private static final Logger log = LogManager.getLogger(CommonExceptionAdvice.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        String requestId = RequestIdFilter.currentRequestId(request);

        log.warn(
                "처리 거절 code={} path={} requestId={} message={}",
                errorCode,
                request.getRequestURI(),
                requestId,
                exception.getMessage()
        );

        return toResponse(errorCode, exception.getMessage(), requestId);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.currentRequestId(request);
        ErrorCode errorCode = isSignupRequest(request)
                ? ErrorCode.INVALID_SIGNUP_INPUT
                : ErrorCode.INVALID_REQUEST;

        log.warn(
                "잘못된 요청 path={} requestId={} message={}",
                request.getRequestURI(),
                requestId,
                exception.getMessage()
        );

        // 파싱 실패 메시지에는 내부 타입·패키지 정보가 섞이므로 그대로 노출하지 않는다.
        return toResponse(errorCode, errorCode.getDefaultMessage(), requestId);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.currentRequestId(request);

        return toResponse(
                ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(),
                requestId
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.currentRequestId(request);

        log.error(
                "처리하지 못한 예외 path={} requestId={}",
                request.getRequestURI(),
                requestId,
                exception
        );

        // 내부 예외 메시지는 사용자에게 노출하지 않는다.
        return toResponse(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                requestId
        );
    }

    private boolean isSignupRequest(HttpServletRequest request) {
        return request != null && request.getRequestURI().endsWith("/api/auth/signup");
    }

    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode,
            String message,
            String requestId
    ) {
        String body = message == null || message.isBlank()
                ? errorCode.getDefaultMessage()
                : message;

        return ResponseEntity
                .status(errorCode.getStatus())
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .body(ErrorResponse.of(errorCode.name(), body, requestId));
    }
}
