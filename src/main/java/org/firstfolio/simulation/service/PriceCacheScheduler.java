package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 장중에 시세를 받아 {@link PriceCache}를 갱신한다 (2026-08-07 확정).
 *
 * <p><b>DB에 쓰지 않는다.</b> 2초마다 저장하면 월 720MB가 쌓이는데 과거 가격을 읽는 코드가
 * 하나도 없다. 종가만 따로 저장한다.</p>
 *
 * <h3>장중에는 캐시, 마감 뒤에는 종가 한 번</h3>
 *
 * <p>정규장에는 캐시만 갱신하고, <b>마감 뒤 첫 틱</b>이 그날의 종가를 DB에 한 번 남긴다.
 * 그 뒤로는 다음 개장까지 아무것도 하지 않는다 — 마감 후 토스가 돌려주는 값은 이미 저장한
 * 것과 같다.</p>
 *
 * <p>개장 전(평일 오전)에는 저장하지 않는다. 그때 저장하면 <b>전날 종가가 오늘 종가로</b>
 * 다시 들어간다.</p>
 *
 * <h3>실패해도 다음 주기에 다시 온다</h3>
 *
 * <p>예외를 안에서 삼킨다. 토스는 <b>IP 화이트리스트</b>라 배포 IP가 바뀌면 모든 호출이
 * {@code 403}으로 막히는데, 그 예외가 스케줄러 밖으로 나가면 반복 실행이 통째로 멈출 수 있다.
 * 한 번 실패해도 캐시에는 직전 값이 남아 있고 평가·거래는 계속된다.</p>
 *
 * <p>2초마다 도는 작업이라 <b>정상 경로는 로그를 남기지 않는다</b> — 하루 11,700줄이 된다.
 * <b>실패도 장애당 한 번만 남기고 복구될 때 한 줄 더 남긴다.</b> 지금 상태가 궁금하면
 * {@code GET /api/internal/product-prices/cache}로 캐시를 직접 들여다볼 수 있다.</p>
 */
@Component
public class PriceCacheScheduler {

    /** 캐시에 담는 가격의 출처 표기. 저장 경로와 같은 값을 쓴다. */
    private static final String SOURCE_TYPE_REAL = "REAL_DATA";

    private static final Logger log = LogManager.getLogger(PriceCacheScheduler.class);

    private final TradingHours tradingHours;
    private final PriceQuoteFetcher quoteFetcher;
    private final PriceRefreshService priceRefreshService;
    private final PriceCache priceCache;
    private final Clock clock;

    /** 종가를 저장한 거래일(KST). 하루에 한 번만 저장하기 위한 표시다. */
    private final AtomicReference<LocalDate> closingSavedOn = new AtomicReference<>();

    /** 종가 저장에 실패한 거래일(KST). 같은 날 경고를 한 번만 남기려는 것이다. */
    private final AtomicReference<LocalDate> closingFailedOn = new AtomicReference<>();

    /** 연속 폴링 실패 횟수. 경고를 장애당 한 번만 남기려는 것이다. */
    private final AtomicLong consecutiveFailures = new AtomicLong();

    /**
     * 폴링을 켤지 여부.
     *
     * <p>토스 IP 화이트리스트에 등록되지 않은 환경에서는 켜 봐야 2초마다 {@code 403}만 쌓인다.
     * 그런 곳에서는 꺼 두고 DB 종가로 운영한다.</p>
     */
    private final boolean enabled;

    public PriceCacheScheduler(
            TradingHours tradingHours,
            PriceQuoteFetcher quoteFetcher,
            PriceRefreshService priceRefreshService,
            PriceCache priceCache,
            Clock clock,
            @Value("${price.cache.enabled:true}") boolean enabled
    ) {
        this.tradingHours = tradingHours;
        this.quoteFetcher = quoteFetcher;
        this.priceRefreshService = priceRefreshService;
        this.priceCache = priceCache;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * 주기는 <b>가정치</b>다 (v3 7절 미정). 기본 2초.
     *
     * <p>토스 {@code MARKET_DATA}는 초당 10회 제한인데 15종목을 한 번의 호출로 가져오므로
     * 2초 주기는 0.5 req/s — 한도의 5%다.</p>
     *
     * <p>{@code fixedDelay}다. 응답이 2초를 넘어도 호출이 겹쳐 쌓이지 않는다.</p>
     */
    @Scheduled(fixedDelayString = "${price.cache.interval-millis:2000}")
    public void pollDuringSession() {
        if (!enabled) {
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now(clock);

            if (tradingHours.isMarketOpen(now)) {
                refresh(now);
                reportRecovery();

                return;
            }

            // 마감 뒤 첫 틱이 그날의 종가를 남긴다. 개장 전 시간대에는 해당되지 않는다.
            if (tradingHours.isAfterClose(now)) {
                saveClosingPrice(now);
            }
        } catch (Exception exception) {
            // 밖으로 내보내면 반복 실행이 멈출 수 있다. 직전 캐시로 계속 돈다.
            reportFailure(exception);
        }
    }

    /**
     * 폴링 실패를 알린다. <b>연속 실패 중에는 첫 번째만 남긴다.</b>
     *
     * <p>2초 주기라 그냥 찍으면 정규장 내내 막혔을 때 <b>하루 11,700줄</b>이 스택트레이스와 함께
     * 쌓인다. 실제로 토스 IP 화이트리스트가 풀려 4분 만에 120줄이 찍힌 적이 있다 (2026-08-10).</p>
     *
     * <p>같은 장애가 계속되는 동안 줄이 늘어나 봐야 알려주는 것이 없다. 대신 <b>복구될 때
     * 한 줄 더 남겨</b> 장애 구간을 로그만으로 알 수 있게 한다. 현재 상태는
     * {@code GET /api/internal/product-prices/cache}로 언제든 확인할 수 있다.</p>
     */
    private void reportFailure(Exception exception) {
        if (consecutiveFailures.getAndIncrement() == 0) {
            log.warn(
                    "시세 폴링에 실패했습니다. 다음 주기에 다시 시도하며, 복구될 때까지 이 경고를 반복하지 않습니다.",
                    exception
            );
        }
    }

    /**
     * 실패하다 성공했으면 복구를 알린다.
     *
     * <p><b>실제로 시도해서 성공했을 때만 부른다.</b> 장외에는 아무것도 하지 않으므로
     * 마감 시각에 "복구됐다"고 잘못 알리지 않는다.</p>
     */
    private void reportRecovery() {
        long failures = consecutiveFailures.getAndSet(0);

        if (failures > 0) {
            log.info("시세 폴링이 복구되었습니다. 연속 실패 {}회", failures);
        }
    }

    /**
     * 그날의 종가를 {@code product_prices}에 하루 한 번 남긴다.
     *
     * <p><b>"종가"는 정규장 종료 후 첫 조회값이다.</b> 마감 뒤에도 토스는 마지막 체결가를 계속
     * 돌려주므로 그 값을 그대로 쓴다.</p>
     *
     * <h3>왜 저장하는가</h3>
     *
     * <p>장중 가격은 메모리에만 있어서 앱이 재시작하면 사라진다. 종가가 DB에 남아 있어야
     * <b>금요일 밤에 재시작해도 주말 내내 평가가 된다.</b> 하루 15행이라 삭제 배치도 필요 없다.</p>
     *
     * <h3>두 번 저장되지 않는다</h3>
     *
     * <p>날짜 표시로 하루 한 번을 지킨다. 표시는 메모리에만 있어서 <b>재시작하면 날아가므로</b>
     * 그때는 DB에 오늘 마감 후 저장된 행이 있는지 확인한다.</p>
     *
     * <p>원래는 {@code generation_key} 유니크 제약이 막아 줄 것으로 봤는데
     * <b>실측에서 뒤집혔다</b> (2026-08-10). 시간외 거래(16:00~18:00) 중에는 시세 갱신 시각이
     * 계속 바뀌어 키가 매번 달라진다 — 90초 간격으로 두 번 부르니 15건 중 9건이 새 행으로
     * 저장됐다. 그래서 제약에 기대지 않고 직접 확인한다.</p>
     *
     * <p><b>실패하면 다음 주기에 다시 시도한다.</b> 표시는 성공한 뒤에만 남긴다. 다만 경고는
     * 그날 한 번만 남긴다 — 2초마다 실패하면 로그가 하룻밤에 3만 줄이 된다.</p>
     */
    private void saveClosingPrice(LocalDateTime nowUtc) {
        LocalDate today = tradingHours.koreaDate(nowUtc);

        if (today.equals(closingSavedOn.get())) {
            return;
        }

        // 재시작으로 표시가 날아갔을 수 있다. 이미 남아 있으면 그것이 오늘의 종가다.
        if (priceRefreshService.hasPricesSince(tradingHours.closeAtUtc(today))) {
            closingSavedOn.set(today);

            log.info("오늘 종가가 이미 저장돼 있어 건너뜁니다 거래일={}", today);

            return;
        }

        try {
            PriceRefreshResult result = priceRefreshService.refresh(nowUtc, null);

            closingSavedOn.set(today);
            closingFailedOn.set(null);

            log.info(
                    "종가 저장 완료 거래일={} 대상={} 저장={} 건너뜀={}",
                    today,
                    result.getProcessedCount(),
                    result.getCreatedCount(),
                    result.getSkippedCount()
            );
        } catch (Exception exception) {
            if (!today.equals(closingFailedOn.getAndSet(today))) {
                log.warn("종가 저장에 실패했습니다. 다음 주기에 다시 시도합니다 거래일=" + today, exception);
            }
        }
    }

    /**
     * 시세를 받아 캐시를 덮어쓴다.
     *
     * @param referenceAt 이 폴링의 기준 시각(UTC). 응답의 체결 시각이 아니라 <b>조회 시점</b>을
     *                    쓴다 — 종목마다 마지막 체결 시각이 흩어져 있어, 그대로 쓰면
     *                    "같은 시점의 포트폴리오 평가"가 성립하지 않는다 (저장 경로와 같은 규칙)
     * @return 캐시에 넣은 종목 수
     */
    int refresh(LocalDateTime referenceAt) {
        List<FinancialProduct> targets = quoteFetcher.findTargets(null);
        Map<String, FinancialProduct> bySymbol = quoteFetcher.indexBySymbol(targets);

        if (bySymbol.isEmpty()) {
            return 0;
        }

        Map<String, TossPricesResponse.Item> quotes = quoteFetcher.fetchQuotes(bySymbol.keySet());
        List<ProductPrice> refreshed = new ArrayList<>();

        for (Map.Entry<String, FinancialProduct> entry : bySymbol.entrySet()) {
            BigDecimal price = quoteFetcher.lastPrice(quotes.get(entry.getKey()));

            // 값이 없으면 넣지 않는다. 캐시에 남아 있는 직전 값이 마지막 유효 가격이다 (FUNC-036).
            if (price == null) {
                continue;
            }

            refreshed.add(toProductPrice(entry.getValue(), price, referenceAt));
        }

        priceCache.putAll(refreshed);

        return refreshed.size();
    }

    /**
     * 캐시에 담을 한 건.
     *
     * <p>{@code generationKey}는 넣지 않는다 — 저장할 때 중복을 막는 DB 쪽 장치라
     * 메모리에서는 쓸 곳이 없다.</p>
     */
    private static ProductPrice toProductPrice(
            FinancialProduct product,
            BigDecimal price,
            LocalDateTime referenceAt
    ) {
        ProductPrice cached = new ProductPrice();

        cached.setProductId(product.getProductId());
        cached.setPrice(price);
        cached.setReferenceAt(referenceAt);
        cached.setSourceType(SOURCE_TYPE_REAL);

        return cached;
    }
}
