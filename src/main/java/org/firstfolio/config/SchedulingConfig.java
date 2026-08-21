package org.firstfolio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 앱이 스스로 주기 작업을 돌리게 하는 설정.
 *
 * <p>이 설정이 로드되기 전까지 이 앱은 <b>HTTP 요청이 올 때만</b> 움직였다. 시세 갱신도
 * 자산 이벤트 반영도 사람이 내부 API를 불러야 했다.</p>
 *
 * <h3>주기 작업을 추가하는 사람에게</h3>
 *
 * <p>{@code @Scheduled}를 붙인 {@code @Component}를 만들면 바로 돈다. 이 설정을 다시
 * 건드릴 필요는 없다. <b>다만 {@code scheduling.pool-size}를 함께 늘려라</b> —
 * 스레드는 앱 전체가 나눠 쓴다. 작업 수보다 스레드가 적으면 한쪽이 도는 동안 다른 쪽이
 * 순서를 기다린다. 오래 걸리는 작업 하나가 짧은 작업을 통째로 멈춰 세울 수 있다.</p>
 *
 * <h3>테스트에서는 로드되지 않는다</h3>
 *
 * <p>{@link RootConfig}가 아니라 {@link WebConfig#getRootConfigClasses()}에서만 등록한다.
 * 테스트는 {@code RootConfig}만 올리므로 스케줄이 활성화되지 않는다 —
 * <b>단위·jdbc 테스트가 2초마다 외부 API를 부르면 안 된다.</b> 스케줄 대상 빈 자체는
 * 컴포넌트 스캔으로 만들어지므로, 메서드를 직접 불러 검증하는 데는 문제가 없다.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 주기 작업이 나눠 쓰는 스레드 풀.
     *
     * <p>스프링 기본값은 <b>1</b>이라 작업이 둘만 돼도 서로를 막는다. 기본 3은
     * 시세 폴링, 일봉 동기화, 자산 이벤트 배치를 염두에 둔 값이다.</p>
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${scheduling.pool-size:3}") int poolSize
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("firstfolio-sched-");

        // 종료 시 돌던 작업이 끝나기를 기다린다. 시세 폴링은 짧지만 배치는 DB를 만진다.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);

        return scheduler;
    }
}
