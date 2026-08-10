package org.firstfolio.portfolio.domain;

/**
 * {@code portfolio_transactions.transaction_type} CHECK 제약과 값이 같아야 한다.
 *
 * <p>거래(매수·매도)와 자산 이벤트(이자·배당·만기), 지급·초기화가 한 테이블에 함께 쌓인다.</p>
 */
public enum TransactionType {

    /** 포트폴리오 기초 과정 완료 시 지급하는 3천만원 (FUNC-029). */
    INITIAL_GRANT,

    BUY,
    SELL,

    /** 예·적금 이자, 채권 이표 (FUNC-041). */
    INTEREST,

    /** 주식 배당 (FUNC-041). */
    DIVIDEND,

    /** 만기 상환 (FUNC-041). */
    MATURITY,

    /** 파산 신청에 따른 초기화 (FUNC-037). */
    RESET
}
