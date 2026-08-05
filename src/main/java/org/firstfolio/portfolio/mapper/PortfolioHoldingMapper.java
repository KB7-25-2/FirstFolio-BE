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
}
