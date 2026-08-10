package org.firstfolio.portfolio.domain;

/**
 * 보유 상품의 상태. {@code portfolio_holdings.status} CHECK 제약과 값이 같아야 한다.
 *
 * <p>평가 대상은 {@code ACTIVE}뿐이다. {@code MATURED}·{@code SOLD}는 이미 현금으로
 * 돌아온 자리라 다시 더하면 이중 계산이 된다 (FUNC-036).</p>
 */
public enum HoldingStatus {

    ACTIVE,
    MATURED,
    SOLD
}
