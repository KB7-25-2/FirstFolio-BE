package org.firstfolio.exception;

import org.springframework.http.HttpStatus;

/**
 * API_DOCS.md에 정의된 오류 코드와 HTTP 상태의 대응표.
 *
 * <p>담당 범위(FUNC-029~042, Portfolio / Product Simulation)의 코드만 담는다.
 * 다른 도메인이 자기 코드를 추가할 때도 이 enum을 함께 사용한다.</p>
 */
public enum ErrorCode {

    // 인증·회원
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "Firebase ID Token이 없거나 유효하지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 유효하지 않습니다."),
    SIGNUP_REQUIRED(HttpStatus.CONFLICT, "Firebase 인증은 완료됐지만 FirstFolio 회원 정보가 없습니다."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정 상태입니다."),
    INVALID_SIGNUP_INPUT(HttpStatus.BAD_REQUEST, "가입 정보 또는 필수 약관 동의가 올바르지 않습니다."),
    ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "이미 사용 중인 이메일, 인증 계정 또는 닉네임입니다."),
    NO_PATCH_FIELDS(HttpStatus.BAD_REQUEST, "변경할 프로필 필드가 없습니다."),
    NICKNAME_CONFLICT(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // 대·소단원 메타데이터
    MAIN_CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "대단원을 찾을 수 없습니다."),
    SUB_CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "소단원을 찾을 수 없습니다."),
    INVALID_MAIN_CHAPTER(HttpStatus.UNPROCESSABLE_ENTITY, "대단원 정보가 올바르지 않습니다."),
    SUB_CHAPTER_ORDER_CONFLICT(HttpStatus.CONFLICT, "같은 순서의 소단원이 이미 존재합니다."),
    FOUNDATION_CONFLICT(HttpStatus.CONFLICT, "활성 포트폴리오 기초 과정이 이미 존재합니다."),

    // 강좌 콘텐츠 버전
    CONTENT_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "강좌 콘텐츠 버전을 찾을 수 없습니다."),
    CONTENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "같은 소단원 콘텐츠 버전이 이미 존재합니다."),
    CONTENT_NOT_PUBLISHABLE(HttpStatus.CONFLICT, "공개할 수 없는 강좌 콘텐츠 버전입니다."),
    CONTENT_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "강좌 콘텐츠 검증에 실패했습니다."),

    // 포트폴리오 (FUNC-033, 034, 036)
    PORTFOLIO_ALREADY_CONFIGURED(HttpStatus.CONFLICT, "이미 최초 포트폴리오를 구성했습니다."),
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
