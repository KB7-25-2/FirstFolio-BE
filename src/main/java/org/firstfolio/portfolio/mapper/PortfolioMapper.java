package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.Portfolio;

@Mapper
public interface PortfolioMapper {

    /** 사용자의 현재 활성 포트폴리오. 없으면 null이다. */
    Portfolio findActiveByUserId(@Param("userId") Long userId);

    Portfolio findById(@Param("portfolioId") Long portfolioId);

    void insert(Portfolio portfolio);
}
