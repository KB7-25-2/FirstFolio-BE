package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.PortfolioTransaction;

@Mapper
public interface PortfolioTransactionMapper {

    /**
     * 같은 요청이 이미 처리됐는지 확인한다. 중복 처리를 막는 1차 방어선이다.
     * (2차는 {@code uq_portfolio_transactions_idempotency} 유니크 제약)
     */
    PortfolioTransaction findByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey
    );

    void insert(PortfolioTransaction transaction);
}
