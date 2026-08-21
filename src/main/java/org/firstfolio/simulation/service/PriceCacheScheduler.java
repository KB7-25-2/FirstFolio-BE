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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 장중에 시세를 받아 {@link PriceCache}를 갱신한다 (2026-08-07 확정).
 *
 * <p><b>DB에 쓰지 않는다.</b> 2초마다 저장하면 월 720MB가 쌓인다. 장중 값은 메모리에만 두고,
 * 마감 뒤 확정 일봉은 별도 스케줄러가 저장한다.</p>
 *
 * <h3>장중에는 현재가·당일 OHLC 캐시만 갱신</h3>
 *
 * <p>정규장에는 현재가와 당일 OHLC를 메모리에서만 갱신한다. 확정 일봉 저장은
 * {@link ProductCandleScheduler}가 담당하며, 이 스케줄러는 DB에 쓰지 않는다.</p>
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
    private final PriceCache priceCache;
    private final IntradayCandleCache intradayCandleCache;
    private final Clock clock;

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
            PriceCache priceCache,
            IntradayCandleCache intradayCandleCache,
            Clock clock,
            @Value("${price.cache.enabled:true}") boolean enabled
    ) {
        this.tradingHours = tradingHours;
        this.quoteFetcher = quoteFetcher;
        this.priceCache = priceCache;
        this.intradayCandleCache = intradayCandleCache;
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
            TossPricesResponse.Item quote = quotes.get(entry.getKey());
            BigDecimal price = quoteFetcher.lastPrice(quote);

            // 값이 없으면 넣지 않는다. 캐시에 남아 있는 직전 값이 마지막 유효 가격이다 (FUNC-036).
            if (price == null) {
                continue;
            }

            refreshed.add(toProductPrice(entry.getValue(), price, referenceAt));
            intradayCandleCache.update(
                    entry.getValue().getProductId(),
                    tradingHours.koreaDate(referenceAt),
                    price,
                    quote == null ? null : quote.getCurrency(),
                    referenceAt
            );
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
