package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.PortfolioHolding;

import java.util.List;

@Mapper
public interface PortfolioHoldingMapper {

    /**
     * 평가 대상 보유 상품. 상품의 가명·자산군을 함께 읽는다.
     *
     * <p>이미 만기·매도된 보유는 현금으로 돌아왔으므로 제외한다.</p>
     */
    List<PortfolioHolding> findActiveByPortfolioId(@Param("portfolioId") Long portfolioId);

    /**
     * 한 상품의 보유. <b>상태를 가리지 않는다</b> — 없으면 null이다.
     *
     * <p>{@code ACTIVE}만 찾으면 안 되는 이유가 있다. 전량 매도한 보유는 {@code SOLD}로 남는데,
     * {@code uq_portfolio_holdings_product (portfolio_id, product_id)} 때문에 같은 상품을 다시 살 때
     * <b>새로 INSERT할 수 없다.</b> 행을 지우는 것도 불가능하다 —
     * {@code portfolio_transactions.holding_id}가 FK로 참조하고 {@code ON DELETE RESTRICT}라
     * 거래 이력이 있는 한 막힌다. 그래서 <b>기존 행을 되살리는 것 말고 방법이 없다.</b></p>
     *
     * <p>가입형 재매수 차단도 이 메서드로 판정한다 — {@code ACTIVE}일 때만 막고,
     * 해지한 뒤에는 다시 가입할 수 있어야 한다.</p>
     */
    PortfolioHolding findByPortfolioAndProduct(
            @Param("portfolioId") Long portfolioId,
            @Param("productId") Long productId
    );

    void insert(PortfolioHolding holding);

    /**
     * 수량·원금·평균단가·상태를 갱신한다.
     *
     * <p>전량 매도로 수량이 0이 되면 {@code SOLD}로, {@code SOLD}였던 상품을 다시 사면
     * {@code ACTIVE}로 되돌리는 것도 이 메서드가 한다.</p>
     */
    int update(PortfolioHolding holding);
}
