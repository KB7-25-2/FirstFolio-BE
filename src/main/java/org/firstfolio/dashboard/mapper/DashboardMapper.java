package org.firstfolio.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.PortfolioTransaction;

import java.util.List;

@Mapper
public interface DashboardMapper {

    /** portfolio_id 기준, 아직 도래하지 않은 예정 이벤트를 가까운 순으로 최대 limit건. */
    List<PortfolioTransaction> findUpcomingEvents(
            @Param("portfolioId") Long portfolioId,
            @Param("limit") int limit
    );
}
