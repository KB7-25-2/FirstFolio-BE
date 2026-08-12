package org.firstfolio.simulation.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceCacheSchedulerTest {

    private static final long STOCK_ID = 87L;
    private static final long FUND_ID = 88L;

    /** 2026-08-06(목) 12:00 KST — 장중. */
    private static final String DURING_SESSION = "2026-08-06T12:00:00";
    /** 2026-08-06(목) 18:00 KST — 마감 후. */
    private static final String AFTER_CLOSE = "2026-08-06T18:00:00";
    /** 2026-08-08(토) 12:00 KST — 주말. */
    private static final String WEEKEND = "2026-08-08T12:00:00";

    private FinancialProductMapper productMapper;
    private TossInvestClient tossClient;
    private PriceRefreshService priceRefreshService;
    private PriceCache priceCache;

    private final List<FinancialProduct> targets = new ArrayList<>();
    private final List<TossPricesResponse.Item> quotes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        productMapper = mock(FinancialProductMapper.class);
        tossClient = mock(TossInvestClient.class);
        priceRefreshService = mock(PriceRefreshService.class);
        priceCache = new PriceCache();

        targets.clear();
        quotes.clear();

        when(productMapper.findPriceTargets(anyList(), any())).thenReturn(targets);
        when(tossClient.fetchPrices(anyList())).thenReturn(quotes);
        when(priceRefreshService.refresh(any(), any()))
                .thenReturn(new PriceRefreshResult(null, 15, 15, 0));
        when(priceRefreshService.hasPricesSince(any())).thenReturn(false);
    }

    /** 한국 시각을 서버가 쓰는 UTC로 바꾼다. */
    private static LocalDateTime utcOf(String koreaDateTime) {
        return ZonedDateTime.of(LocalDateTime.parse(koreaDateTime), ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private PriceCacheScheduler schedulerAt(String koreaDateTime, boolean enabled) {
        Clock fixed = Clock.fixed(
                utcOf(koreaDateTime).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );

        return new PriceCacheScheduler(
                new TradingHours(),
                new PriceQuoteFetcher(productMapper, tossClient),
                priceRefreshService,
                priceCache,
                fixed,
                enabled
        );
    }

    private PriceCacheScheduler schedulerAt(String koreaDateTime) {
        return schedulerAt(koreaDateTime, true);
    }

    private void target(long productId, AssetType assetType, String symbol) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(productId);
        product.setAssetType(assetType);
        product.setSourceProductCode(symbol);

        targets.add(product);
    }

    private void quote(String symbol, String lastPrice) {
        TossPricesResponse.Item item = new TossPricesResponse.Item();

        item.setSymbol(symbol);
        item.setLastPrice(lastPrice == null ? null : new BigDecimal(lastPrice));
        item.setTimestamp("2026-08-06T12:00:00.000+09:00");

        quotes.add(item);
    }

    // ------------------------------------------------------------- 언제 도는가

    @Test
    @DisplayName("장중에는 시세를 받아 캐시를 채운다")
    void fillsCacheDuringSession() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");

        schedulerAt(DURING_SESSION).pollDuringSession();

        assertNotNull(priceCache.find(STOCK_ID));
        assertEquals(new BigDecimal("241500.0000"), priceCache.find(STOCK_ID).getPrice());
    }

    @Test
    @DisplayName("마감 후에는 캐시를 갱신하지 않는다")
    void doesNotRefreshCacheAfterClose() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");

        schedulerAt(AFTER_CLOSE).pollDuringSession();

        verify(tossClient, never()).fetchPrices(anyList());
        assertEquals(0, priceCache.size());
    }

    @Test
    @DisplayName("주말에는 토스를 부르지 않는다")
    void doesNotPollOnWeekend() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");

        schedulerAt(WEEKEND).pollDuringSession();

        verify(tossClient, never()).fetchPrices(anyList());
    }

    @Test
    @DisplayName("꺼져 있으면 장중에도 아무것도 하지 않는다 — 토스 IP 미등록 환경용")
    void doesNothingWhenDisabled() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");

        schedulerAt(DURING_SESSION, false).pollDuringSession();

        verify(productMapper, never()).findPriceTargets(anyList(), any());
        verify(tossClient, never()).fetchPrices(anyList());
    }

    // ------------------------------------------------------------- 실패해도 멈추지 않는다

    @Test
    @DisplayName("토스가 실패해도 예외를 밖으로 내보내지 않는다 — 반복 실행이 멈추면 안 된다")
    void swallowsFailureSoScheduleKeepsRunning() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        when(tossClient.fetchPrices(anyList()))
                .thenThrow(new IllegalStateException("403 access_denied (IP 미등록)"));

        PriceCacheScheduler scheduler = schedulerAt(DURING_SESSION);

        // 스케줄 메서드는 조용히 넘어간다.
        scheduler.pollDuringSession();

        // 같은 실패가 refresh를 직접 부르면 그대로 드러나는지도 확인한다 — 삼키는 자리가 한 곳이어야 한다.
        assertThrows(IllegalStateException.class, () -> scheduler.refresh(utcOf(DURING_SESSION)));
    }

    @Test
    @DisplayName("실패해도 직전 캐시는 남는다 — 평가·거래가 계속된다")
    void keepsPreviousCacheOnFailure() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");
        schedulerAt(DURING_SESSION).pollDuringSession();

        when(tossClient.fetchPrices(anyList())).thenThrow(new IllegalStateException("일시 장애"));
        schedulerAt(DURING_SESSION).pollDuringSession();

        assertEquals(new BigDecimal("241500.0000"), priceCache.find(STOCK_ID).getPrice());
    }

    // ------------------------------------------------------------- 무엇을 넣는가

    @Test
    @DisplayName("주식과 펀드를 한 번의 호출로 가져온다")
    void fetchesStockAndFundTogether() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        target(FUND_ID, AssetType.FUND, "069500");
        quote("005930", "241500");
        quote("069500", "72300");

        assertEquals(2, schedulerAt(DURING_SESSION).refresh(utcOf(DURING_SESSION)));

        verify(tossClient).fetchPrices(List.of("005930", "069500"));
    }

    @Test
    @DisplayName("응답에 없는 종목은 넣지 않는다 — 캐시의 직전 값이 마지막 유효 가격이다")
    void skipsMissingQuote() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        target(FUND_ID, AssetType.FUND, "069500");
        quote("005930", "241500");

        assertEquals(1, schedulerAt(DURING_SESSION).refresh(utcOf(DURING_SESSION)));
        assertNull(priceCache.find(FUND_ID));
    }

    @Test
    @DisplayName("가격이 0이거나 없으면 넣지 않는다 — 없는 가격을 만들지 않는다")
    void skipsInvalidPrice() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        target(FUND_ID, AssetType.FUND, "069500");
        quote("005930", "0");
        quote("069500", null);

        assertEquals(0, schedulerAt(DURING_SESSION).refresh(utcOf(DURING_SESSION)));
        assertEquals(0, priceCache.size());
    }

    @Test
    @DisplayName("종목코드가 없는 상품은 건너뛴다")
    void skipsProductWithoutSymbol() {
        target(STOCK_ID, AssetType.STOCK, null);

        assertEquals(0, schedulerAt(DURING_SESSION).refresh(utcOf(DURING_SESSION)));
        verify(tossClient, never()).fetchPrices(anyList());
    }

    @Test
    @DisplayName("기준 시각은 체결 시각이 아니라 조회 시점이다 — 종목마다 흩어지면 같은 시점의 평가가 안 된다")
    void usesPollingTimeAsReference() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");   // 체결 시각은 12:00 KST로 고정돼 있다

        LocalDateTime polledAt = utcOf("2026-08-06T14:20:00");

        schedulerAt(DURING_SESSION).refresh(polledAt);

        assertEquals(polledAt, priceCache.find(STOCK_ID).getReferenceAt());
    }

    @Test
    @DisplayName("가격 자릿수를 저장 경로와 맞춘다 — 재시작 전후로 평가액이 달라지면 안 된다")
    void matchesStoredPriceScale() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500.12345");

        schedulerAt(DURING_SESSION).refresh(utcOf(DURING_SESSION));

        assertEquals(new BigDecimal("241500.1235"), priceCache.find(STOCK_ID).getPrice());
    }

    @Test
    @DisplayName("다음 폴링이 값을 덮어쓴다")
    void overwritesOnNextPoll() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");
        schedulerAt(DURING_SESSION).pollDuringSession();

        quotes.clear();
        quote("005930", "242000");
        schedulerAt(DURING_SESSION).pollDuringSession();

        assertEquals(new BigDecimal("242000.0000"), priceCache.find(STOCK_ID).getPrice());
        assertEquals(1, priceCache.size());
    }

    @Test
    @DisplayName("대상 상품이 없으면 토스를 부르지 않는다")
    void doesNotCallTossWithoutTargets() {
        assertEquals(0, schedulerAt(DURING_SESSION).refresh(utcOf(DURING_SESSION)));
        verify(tossClient, never()).fetchPrices(anyList());
    }

    @Test
    @DisplayName("15:30 정각은 아직 장중이다 — 마감 체결까지 받는다")
    void pollsAtClosingMinute() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");

        schedulerAt("2026-08-06T15:30:00").pollDuringSession();

        assertNotNull(priceCache.find(STOCK_ID));
    }

    // ------------------------------------------------------------- 종가 저장

    @Test
    @DisplayName("마감 뒤 첫 틱이 그날의 종가를 저장한다")
    void savesClosingPriceAfterClose() {
        schedulerAt(AFTER_CLOSE).pollDuringSession();

        verify(priceRefreshService).refresh(utcOf(AFTER_CLOSE), null);
    }

    @Test
    @DisplayName("같은 날 두 번째 틱부터는 저장하지 않는다 — 하루 15행이면 충분하다")
    void savesClosingPriceOnlyOncePerDay() {
        PriceCacheScheduler scheduler = schedulerAt(AFTER_CLOSE);

        scheduler.pollDuringSession();
        scheduler.pollDuringSession();
        scheduler.pollDuringSession();

        verify(priceRefreshService, times(1)).refresh(any(), any());
    }

    @Test
    @DisplayName("개장 전에는 저장하지 않는다 — 전날 종가가 오늘 종가로 다시 들어간다")
    void doesNotSaveBeforeOpen() {
        // 평일 08:00 KST. 장은 닫혀 있지만 오늘 장이 끝난 것은 아니다.
        schedulerAt("2026-08-06T08:00:00").pollDuringSession();

        verify(priceRefreshService, never()).refresh(any(), any());
    }

    @Test
    @DisplayName("주말에는 저장하지 않는다")
    void doesNotSaveOnWeekend() {
        schedulerAt(WEEKEND).pollDuringSession();
        schedulerAt("2026-08-08T18:00:00").pollDuringSession();
        schedulerAt("2026-08-09T18:00:00").pollDuringSession();

        verify(priceRefreshService, never()).refresh(any(), any());
    }

    @Test
    @DisplayName("장중에는 저장하지 않는다 — 2초마다 쌓으면 월 720MB다")
    void doesNotSaveDuringSession() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");

        schedulerAt(DURING_SESSION).pollDuringSession();

        verify(priceRefreshService, never()).refresh(any(), any());
    }

    @Test
    @DisplayName("거래일이 바뀌면 다시 저장한다")
    void savesAgainOnNextTradingDay() {
        PriceCacheScheduler thursday = schedulerAt(AFTER_CLOSE);
        thursday.pollDuringSession();

        // 같은 인스턴스가 다음 날을 맞는 상황이라 스케줄러를 새로 만들지 않는다.
        PriceCacheScheduler friday = schedulerAt("2026-08-07T18:00:00");
        friday.pollDuringSession();

        verify(priceRefreshService).refresh(utcOf(AFTER_CLOSE), null);
        verify(priceRefreshService).refresh(utcOf("2026-08-07T18:00:00"), null);
    }

    @Test
    @DisplayName("저장에 실패하면 다음 틱에 다시 시도한다 — 그날 종가를 잃으면 주말을 못 버틴다")
    void retriesClosingPriceUntilItSucceeds() {
        when(priceRefreshService.refresh(any(), any()))
                .thenThrow(new IllegalStateException("일시 장애"))
                .thenReturn(new PriceRefreshResult(null, 15, 15, 0));

        PriceCacheScheduler scheduler = schedulerAt(AFTER_CLOSE);

        scheduler.pollDuringSession();   // 실패
        scheduler.pollDuringSession();   // 성공
        scheduler.pollDuringSession();   // 성공했으므로 더 부르지 않는다

        verify(priceRefreshService, times(2)).refresh(any(), any());
    }

    @Test
    @DisplayName("저장 실패가 예외로 새어 나가지 않는다")
    void doesNotLeakClosingFailure() {
        when(priceRefreshService.refresh(any(), any()))
                .thenThrow(new IllegalStateException("403 access_denied"));

        schedulerAt(AFTER_CLOSE).pollDuringSession();
    }

    // ------------------------------------------------------------- 재시작 후 중복 방지

    /**
     * 표시는 메모리에만 있어 재시작하면 사라진다. 유니크 제약이 막아 줄 것으로 봤으나
     * 2026-08-10 실측에서 뒤집혔다 — 시간외 거래 중에는 시세 갱신 시각이 계속 바뀌어
     * 90초 간격 두 호출에서 15건 중 9건이 새 행으로 저장됐다.
     */
    @Test
    @DisplayName("재시작해도 오늘 종가가 이미 있으면 다시 저장하지 않는다")
    void doesNotSaveAgainWhenTodayAlreadyStored() {
        when(priceRefreshService.hasPricesSince(any())).thenReturn(true);

        schedulerAt(AFTER_CLOSE).pollDuringSession();

        verify(priceRefreshService, never()).refresh(any(), any());
    }

    @Test
    @DisplayName("확인 기준은 오늘 마감 시각이다 — 어제 종가를 오늘 것으로 착각하면 안 된다")
    void checksAgainstTodaysCloseTime() {
        schedulerAt(AFTER_CLOSE).pollDuringSession();

        // 2026-08-06(목) 15:30 KST = 06:30 UTC
        verify(priceRefreshService).hasPricesSince(LocalDateTime.of(2026, 8, 6, 6, 30));
    }

    @Test
    @DisplayName("이미 있어 건너뛰면 그날은 더 확인하지 않는다")
    void remembersSkipForTheRestOfTheDay() {
        when(priceRefreshService.hasPricesSince(any())).thenReturn(true);

        PriceCacheScheduler scheduler = schedulerAt(AFTER_CLOSE);

        scheduler.pollDuringSession();
        scheduler.pollDuringSession();
        scheduler.pollDuringSession();

        verify(priceRefreshService, times(1)).hasPricesSince(any());
    }

    @Test
    @DisplayName("확인 자체가 실패해도 다음 주기에 다시 시도한다")
    void retriesWhenTheCheckItselfFails() {
        when(priceRefreshService.hasPricesSince(any()))
                .thenThrow(new IllegalStateException("DB 일시 장애"))
                .thenReturn(false);

        PriceCacheScheduler scheduler = schedulerAt(AFTER_CLOSE);

        scheduler.pollDuringSession();   // 확인에서 실패
        scheduler.pollDuringSession();   // 확인 통과 후 저장

        verify(priceRefreshService).refresh(utcOf(AFTER_CLOSE), null);
    }

    // ------------------------------------------------------------- 실패 로그 양

    /**
     * 이 클래스의 로그를 가로채는 임시 appender.
     *
     * <p>바꾸려는 것이 <b>로그 양</b>이라 카운터 같은 대리 지표가 아니라 실제로 찍힌 줄을 센다.</p>
     */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        private CapturingAppender() {
            super("priceCacheSchedulerTest", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        long countAtLeast(Level level) {
            return events.stream().filter(event -> event.getLevel().isMoreSpecificThan(level)).count();
        }

        List<String> messagesAt(Level level) {
            return events.stream()
                    .filter(event -> event.getLevel().equals(level))
                    .map(event -> event.getMessage().getFormattedMessage())
                    .collect(Collectors.toList());
        }
    }

    /** 로그를 잡아 둔 채로 작업을 실행한다. */
    private CapturingAppender captureLogs(Runnable work) {
        Logger logger = (Logger) LogManager.getLogger(PriceCacheScheduler.class);
        CapturingAppender appender = new CapturingAppender();

        appender.start();
        logger.addAppender(appender);

        try {
            work.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        return appender;
    }

    @Test
    @DisplayName("연속 실패해도 경고는 한 번만 남긴다 — 2초 주기면 하루 11,700줄이 된다")
    void warnsOncePerOutage() {
        when(tossClient.fetchPrices(anyList()))
                .thenThrow(new IllegalStateException("403 access_denied"));
        target(STOCK_ID, AssetType.STOCK, "005930");

        PriceCacheScheduler scheduler = schedulerAt(DURING_SESSION);

        CapturingAppender logs = captureLogs(() -> {
            for (int i = 0; i < 50; i++) {
                scheduler.pollDuringSession();
            }
        });

        assertEquals(1, logs.countAtLeast(Level.WARN), "50번 실패해도 경고는 한 줄이어야 합니다.");
    }

    @Test
    @DisplayName("복구되면 알린다 — 장애 구간을 로그만으로 알 수 있어야 한다")
    void reportsRecovery() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");
        when(tossClient.fetchPrices(anyList()))
                .thenThrow(new IllegalStateException("일시 장애"))
                .thenThrow(new IllegalStateException("일시 장애"))
                .thenReturn(quotes);

        PriceCacheScheduler scheduler = schedulerAt(DURING_SESSION);

        CapturingAppender logs = captureLogs(() -> {
            scheduler.pollDuringSession();
            scheduler.pollDuringSession();
            scheduler.pollDuringSession();   // 복구
            scheduler.pollDuringSession();   // 이미 정상 — 더 남기지 않는다
        });

        assertEquals(1, logs.messagesAt(Level.WARN).size());
        assertEquals(1, logs.messagesAt(Level.INFO).size(), "복구 알림은 한 번만이어야 합니다.");
        assertTrue(logs.messagesAt(Level.INFO).get(0).contains("연속 실패 2회"));
    }

    @Test
    @DisplayName("복구 뒤 다시 실패하면 또 경고한다 — 새 장애는 새 경고다")
    void warnsAgainAfterRecovery() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        quote("005930", "241500");
        when(tossClient.fetchPrices(anyList()))
                .thenThrow(new IllegalStateException("1차 장애"))
                .thenReturn(quotes)
                .thenThrow(new IllegalStateException("2차 장애"));

        PriceCacheScheduler scheduler = schedulerAt(DURING_SESSION);

        CapturingAppender logs = captureLogs(() -> {
            scheduler.pollDuringSession();
            scheduler.pollDuringSession();
            scheduler.pollDuringSession();
        });

        assertEquals(2, logs.messagesAt(Level.WARN).size());
    }

    @Test
    @DisplayName("장외에는 복구로 치지 않는다 — 시도조차 안 했다")
    void doesNotClaimRecoveryOutsideSession() {
        target(STOCK_ID, AssetType.STOCK, "005930");
        when(tossClient.fetchPrices(anyList()))
                .thenThrow(new IllegalStateException("장애"));

        CapturingAppender logs = captureLogs(() -> {
            schedulerAt(DURING_SESSION).pollDuringSession();   // 실패
            schedulerAt(WEEKEND).pollDuringSession();          // 아무것도 안 함
        });

        assertEquals(0, logs.messagesAt(Level.INFO).size(), "시도도 안 하고 복구를 알리면 안 됩니다.");
    }
}
