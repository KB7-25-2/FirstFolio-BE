package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.Portfolio;

import java.math.BigDecimal;
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

    /**
     * 매수 대금을 현금에서 뺀다 (FUNC-035).
     *
     * <p><b>차감 조건을 SQL에 넣는다.</b> 자바에서 "잔액 − 금액"을 계산해 덮어쓰면, 잔액을 읽은 뒤
     * 쓰기 전에 끼어든 요청이 같은 잔액을 보고 함께 차감해 <b>보유 현금보다 많이 살 수 있다</b>
     * (같은 사용자가 빠르게 두 번 요청하는 경우. 멱등 키가 다르면 중복으로 걸러지지도 않는다).
     * DB가 직접 빼게 하면 그 창이 사라진다.</p>
     *
     * <p>{@code CHECK (cash_balance >= 0)}은 최후 방어선이지 이 문제를 막지 못한다 —
     * 자바가 계산한 값 자체는 음수가 아니기 때문이다.</p>
     *
     * @return 실제로 차감한 행 수. <b>0이면 잔액이 부족하거나 세대가 이미 닫혔다는 뜻</b>이다
     */
    int decreaseCash(
            @Param("portfolioId") Long portfolioId,
            @Param("amount") BigDecimal amount,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 매도 대금을 현금에 더한다 (FUNC-035).
     *
     * @return 0이면 세대가 이미 닫혔다는 뜻이다
     */
    int increaseCash(
            @Param("portfolioId") Long portfolioId,
            @Param("amount") BigDecimal amount,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
