package org.firstfolio.exception;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@RestControllerAdvice
public class CommonExceptionAdvice {

    private static final Logger log = LogManager.getLogger(CommonExceptionAdvice.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);

        log.warn(
                "API request failed. requestId={}, method={}, uri={}, code={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception.getCode()
        );

        return errorResponse(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                requestId
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);
        boolean signupRequest = request.getRequestURI().endsWith("/auth/signup");
        String code = signupRequest ? "INVALID_SIGNUP_INPUT" : "INVALID_REQUEST";
        String message = signupRequest
                ? "가입 정보 또는 필수 약관 동의가 올바르지 않습니다."
                : "요청 본문이 올바르지 않습니다.";

        log.warn(
                "Unreadable API request body. requestId={}, method={}, uri={}, code={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                code
        );

        return errorResponse(HttpStatus.BAD_REQUEST, code, message, requestId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);

        log.error(
                "Unexpected API error. requestId={}, method={}, uri={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버에서 요청을 처리하지 못했습니다.",
                requestId
        );
    }

    private ResponseEntity<ErrorResponse> errorResponse(
            HttpStatus status,
            String code,
            String message,
            String requestId
    ) {
        ErrorResponse body = new ErrorResponse(
                new ErrorResponse.ErrorBody(code, message, requestId)
        );

        return ResponseEntity.status(status)
                .header(REQUEST_ID_HEADER, requestId)
                .body(body);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId != null && requestId.matches("[A-Za-z0-9._-]{1,100}")) {
            return requestId;
        }

        return "req-" + UUID.randomUUID().toString().replace("-", "");
    }
}
