package org.firstfolio.exception;

import org.springframework.http.HttpStatus;

/**
 * API_DOCS.md에 정의된 오류 코드와 HTTP 상태의 대응표.
 *
 * <p>담당 범위(FUNC-029~042, Portfolio / Product Simulation)의 코드만 담는다.
 * 다른 도메인이 자기 코드를 추가할 때도 이 enum을 함께 사용한다.</p>
 */
public enum ErrorCode {

    // 포트폴리오 (FUNC-034, 036)
    // PORTFOLIO_ALREADY_CONFIGURED(409)는 POST /portfolios와 함께 폐기됐다.
    // "최초 구성" 단계 자체가 없어져 두 번 구성할 일이 없다 (2026-08-05 팀 확정).
    INSUFFICIENT_SIMULATION_CASH(HttpStatus.UNPROCESSABLE_ENTITY, "사용 가능한 모의 현금이 부족합니다."),
    ACTIVE_PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "활성 포트폴리오가 없습니다."),

    // 거래 (FUNC-035)
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "다른 요청 내용으로 사용한 중복 키입니다."),
    TRADE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "잔액·수량·시간 또는 정책 조건을 충족하지 않습니다."),

    // 초기화 (FUNC-037)
    RESET_CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST, "확인 문구가 일치하지 않습니다."),
    RESET_POLICY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "초기화 횟수 또는 대기 시간 정책을 충족하지 않습니다."),

    // 상품 조회 (FUNC-031, 032, 039)
    INVALID_PRODUCT_FILTER(HttpStatus.BAD_REQUEST, "상품 필터가 올바르지 않습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "공개 상품을 찾을 수 없습니다."),

    // 관리자 상품 (FUNC-038)
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    INVALID_SOURCE_PRODUCT(HttpStatus.UNPROCESSABLE_ENTITY, "원천 데이터 또는 가명·시뮬레이션 조건이 올바르지 않습니다."),

    // 내부 배치 (FUNC-040, 041, 042)
    INTERNAL_CALL_REQUIRED(HttpStatus.FORBIDDEN, "허용된 내부 호출이 아닙니다."),
    PRICE_POLICY_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "가격 생성 정책 또는 상품 조건이 올바르지 않습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    EVENT_NOT_RETRYABLE(HttpStatus.CONFLICT, "재처리할 수 없는 상태입니다."),

    // 공통 - API_DOCS에 개별 정의가 없는 경우의 기본 코드
    // TODO: AUTHENTICATION_REQUIRED는 API_DOCS에 명시되지 않은 가정값이다. 팀 확정 후 조정한다.
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
