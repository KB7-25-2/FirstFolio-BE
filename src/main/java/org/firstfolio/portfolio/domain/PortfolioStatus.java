package org.firstfolio.portfolio.domain;

/**
 * {@code portfolios.status} CHECK 제약과 값이 같아야 한다.
 */
public enum PortfolioStatus {

    /** 현재 사용 중인 세대. 사용자당 하나만 존재한다. */
    ACTIVE,

    /** 파산 신청으로 종료된 세대. 감사 목적으로 보존한다 (FUNC-037). */
    CLOSED
}
