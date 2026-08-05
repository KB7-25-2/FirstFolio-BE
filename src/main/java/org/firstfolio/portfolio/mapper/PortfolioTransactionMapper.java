package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionType;

import java.util.List;

@Mapper
public interface PortfolioTransactionMapper {

    /**
     * 같은 요청이 이미 처리됐는지 확인한다. 중복 처리를 막는 1차 방어선이다.
     * (2차는 {@code uq_portfolio_transactions_idempotency} 유니크 제약)
     */
    PortfolioTransaction findByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey
    );

    /**
     * 한 포트폴리오 세대의 거래·자산 이벤트 이력을 최신순으로 읽는다 (FUNC-034).
     *
     * <p>세대가 바뀌면 {@code portfolio_id}가 바뀌므로 이전 세대 이력은 자연히 섞이지 않는다.</p>
     *
     * @param transactionType null이면 전체 유형
     * @param cursorId        null이면 첫 페이지. 있으면 이 식별자보다 앞선(더 오래된) 건부터
     * @param limit           다음 페이지 존재 여부를 알려면 원하는 크기보다 한 건 크게 준다
     */
    List<PortfolioTransaction> findPage(
            @Param("portfolioId") Long portfolioId,
            @Param("transactionType") TransactionType transactionType,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    void insert(PortfolioTransaction transaction);
}
