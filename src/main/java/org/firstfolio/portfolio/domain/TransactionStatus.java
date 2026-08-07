package org.firstfolio.portfolio.domain;

/**
 * {@code portfolio_transactions.status} CHECK 제약과 값이 같아야 한다.
 */
public enum TransactionStatus {

    /** 이자·배당·만기처럼 예정 시각이 아직 오지 않은 상태 (FUNC-041). */
    SCHEDULED,

    COMPLETED,
    FAILED,
    CANCELLED
}
