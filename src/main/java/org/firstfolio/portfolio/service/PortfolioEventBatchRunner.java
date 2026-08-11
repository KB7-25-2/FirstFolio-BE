package org.firstfolio.portfolio.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 도래한 자산 이벤트(이자·만기)를 주기적으로 반영한다 (FUNC-041/042).
 *
 * <p>지금까지 {@code POST /api/internal/portfolio-events/process}를 <b>사람이 직접</b> 불러야
 * 예정된 이자가 현금에 들어왔다. 아무도 부르지 않으면 만기가 지나도 돈이 들어오지 않는다.
 * 이 클래스가 그 호출을 대신한다.</p>
 *
 * <h3>{@link AssetEventScheduler}와 다른 것</h3>
 *
 * <p>이름이 비슷하지만 하는 일이 반대다.</p>
 *
 * <table>
 *   <tr><th></th><th>언제</th><th>무엇을</th></tr>
 *   <tr><td>{@code AssetEventScheduler}</td><td>가입할 때 한 번</td>
 *       <td>만기까지의 <b>일정을 만든다</b> ({@code SCHEDULED})</td></tr>
 *   <tr><td>이 클래스</td><td>주기적으로</td>
 *       <td>도래한 일정을 <b>현금에 반영한다</b> ({@code COMPLETED})</td></tr>
 * </table>
 *
 * <h3>기본값이 꺼져 있다</h3>
 *
 * <p>이자 계산식이 <b>아직 승인되지 않은 가정치</b>다
 * ({@code API_CHANGE_PROPOSAL_ASSET_EVENTS_20260807.md}). 자동으로 돌면 그 금액이 사람 손 없이
 * 지급되고, 이미 지급된 이력({@code COMPLETED})은 되돌리지 않는 것이 지금 설계다.
 * <b>계산식이 확정되면 {@code portfolio.event.batch.enabled=true}로 켠다.</b></p>
 *
 * <p>꺼 두어도 내부 API는 그대로 동작하므로 수동 실행에는 지장이 없다.</p>
 *
 * <h3>실패해도 다음 주기에 다시 온다</h3>
 *
 * <p>예외를 안에서 삼킨다. 밖으로 나가면 반복 실행이 통째로 멈출 수 있다. 개별 이벤트의 실패는
 * {@link PortfolioEventService}가 이미 서로 격리하므로, 여기까지 올라오는 것은 조회 자체가
 * 실패한 경우다.</p>
 */
@Component
public class PortfolioEventBatchRunner {

    private static final Logger log = LogManager.getLogger(PortfolioEventBatchRunner.class);

    private final PortfolioEventService portfolioEventService;
    private final Clock clock;
    private final boolean enabled;

    public PortfolioEventBatchRunner(
            PortfolioEventService portfolioEventService,
            Clock clock,
            @Value("${portfolio.event.batch.enabled:false}") boolean enabled
    ) {
        this.portfolioEventService = portfolioEventService;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * 주기는 기본 1분이다.
     *
     * <p>이벤트 예정 시각은 시간 단위라 초 단위로 볼 이유가 없다. 반대로 너무 뜸하면 데모에서
     * "만기가 됐는데 돈이 안 들어온다"로 보인다.</p>
     *
     * <p>{@code fixedDelay}다. 배치가 1분을 넘겨도 호출이 겹치지 않는다 — 겹치면 같은 이벤트를
     * 두 번 집는 경합이 생긴다.</p>
     */
    @Scheduled(fixedDelayString = "${portfolio.event.batch.interval-millis:60000}")
    public void processDueEvents() {
        if (!enabled) {
            return;
        }

        try {
            // 기준은 지금이다. 미래 시각은 서비스가 거부한다.
            portfolioEventService.process(LocalDateTime.now(clock), null);
        } catch (Exception exception) {
            log.warn("자산 이벤트 배치에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}
