package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 확정 일봉 적재와 장중 공식 일봉 교정을 담당하는 스케줄러. */
@Component
public class ProductCandleScheduler {

    private static final Logger log = LogManager.getLogger(ProductCandleScheduler.class);

    private final ProductCandleSyncService syncService;
    private final TradingHours tradingHours;
    private final Clock clock;
    private final boolean enabled;
    private final Duration intradayRefreshInterval;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<LocalDateTime> intradayRefreshedAt = new AtomicReference<>();
    private final AtomicReference<LocalDate> finalizedOn = new AtomicReference<>();

    public ProductCandleScheduler(
            ProductCandleSyncService syncService,
            TradingHours tradingHours,
            Clock clock,
            @Value("${candle.sync.enabled:true}") boolean enabled,
            @Value("${candle.sync.intraday-interval-millis:300000}") long intradayIntervalMillis
    ) {
        this.syncService = syncService;
        this.tradingHours = tradingHours;
        this.clock = clock;
        this.enabled = enabled;
        this.intradayRefreshInterval = Duration.ofMillis(Math.max(intradayIntervalMillis, 60000));
    }

    /**
     * 시작 후 누락 상품을 백필하고, 장중에는 5분마다 공식 일봉으로 OHLCV를 교정한다.
     * 마감 후에는 최근 5봉을 한 번 upsert한다.
     */
    @Scheduled(
            initialDelayString = "${candle.sync.initial-delay-millis:10000}",
            fixedDelayString = "${candle.sync.check-interval-millis:60000}"
    )
    public void maintain() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now(clock);
            CandleSyncResult bootstrap = syncService.bootstrapMissing(now);
            boolean bootstrapped = bootstrap.getProcessedProductCount() > 0;

            if (bootstrapped) {
                // 같은 회차에 200봉 호출 뒤 1봉 호출을 연달아 보내 차트 그룹 burst를 넘기지 않는다.
                intradayRefreshedAt.set(now);
                log.info(
                        "누락 상품 일봉 초기 적재 완료 대상={} 저장봉={} 실패상품={}",
                        bootstrap.getProcessedProductCount(),
                        bootstrap.getSavedCandleCount(),
                        bootstrap.getFailedProductCount()
                );
            }

            if (tradingHours.isMarketOpen(now) && shouldRefreshIntraday(now)) {
                CandleSyncResult result = syncService.refreshIntraday(now);

                if (result.isSuccessful()) {
                    intradayRefreshedAt.set(now);
                }
            }

            // 200봉 백필과 5봉 마감 동기화를 같은 초에 이어 호출하지 않는다.
            if (tradingHours.isAfterClose(now) && !bootstrapped) {
                syncAfterClose(now);
            }
        } catch (Exception exception) {
            log.warn("상품 일봉 유지 작업에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        } finally {
            running.set(false);
        }
    }

    /** 수정주가가 과거 봉까지 바뀌는 경우를 위해 주 1회 최근 200봉을 재조정한다. */
    @Scheduled(cron = "${candle.sync.reconcile-cron:0 0 3 * * SUN}", zone = "Asia/Seoul")
    public void reconcile() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }

        try {
            CandleSyncResult result = syncService.reconcileAll(LocalDateTime.now(clock));

            log.info(
                    "주간 일봉 재조정 완료 대상={} 저장봉={} 실패상품={}",
                    result.getProcessedProductCount(),
                    result.getSavedCandleCount(),
                    result.getFailedProductCount()
            );
        } catch (Exception exception) {
            log.warn("주간 일봉 재조정에 실패했습니다.", exception);
        } finally {
            running.set(false);
        }
    }

    private void syncAfterClose(LocalDateTime now) {
        LocalDate today = tradingHours.koreaDate(now);

        if (today.equals(finalizedOn.get())) {
            return;
        }

        CandleSyncResult result = syncService.syncRecentConfirmed(now);

        if (result.isSuccessful()) {
            finalizedOn.set(today);
            log.info(
                    "장 마감 일봉 동기화 완료 거래일={} 대상={} 저장봉={}",
                    today,
                    result.getProcessedProductCount(),
                    result.getSavedCandleCount()
            );
        }
    }

    private boolean shouldRefreshIntraday(LocalDateTime now) {
        LocalDateTime previous = intradayRefreshedAt.get();

        return previous == null || Duration.between(previous, now).compareTo(intradayRefreshInterval) >= 0;
    }
}
