package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.Portfolio;

import java.time.LocalDateTime;

@Mapper
public interface PortfolioMapper {

    /** 사용자의 현재 활성 포트폴리오. 없으면 null이다. */
    Portfolio findActiveByUserId(@Param("userId") Long userId);

    Portfolio findById(@Param("portfolioId") Long portfolioId);

    void insert(Portfolio portfolio);

    /**
     * 세대를 종료한다 (FUNC-037 초기화).
     *
     * <p><b>{@code ACTIVE}인 세대만 닫는다.</b> 이미 닫힌 세대를 다시 닫으면 0을 돌려주므로,
     * 호출한 쪽이 "다른 요청이 먼저 초기화했다"를 알 수 있다. 조건을 SQL에 넣어 두면
     * 조회와 갱신 사이에 끼어드는 동시 요청을 애플리케이션 락 없이 막을 수 있다.</p>
     *
     * <p>보유 상품({@code portfolio_holdings})은 건드리지 않는다. 세대가 닫힌 것으로 충분하고,
     * 상태를 {@code SOLD}로 바꾸면 <b>팔지도 않은 것을 팔았다고 기록</b>하게 된다
     * (대응하는 {@code SELL} 거래도 없어 두 테이블이 어긋난다).</p>
     *
     * @return 실제로 닫은 행 수. 0이면 이미 닫혀 있었다는 뜻이다
     */
    int closeGeneration(
            @Param("portfolioId") Long portfolioId,
            @Param("closedAt") LocalDateTime closedAt
    );
}
