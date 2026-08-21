package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.IntradayCandle;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntradayCandleCacheTest {

    private static final long PRODUCT_ID = 87L;
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 21);

    @Test
    @DisplayName("2초 현재가 틱으로 고가·저가·종가를 메모리에서 갱신한다")
    void aggregatesPriceTicks() {
        IntradayCandleCache cache = new IntradayCandleCache();

        cache.update(PRODUCT_ID, TRADE_DATE, new BigDecimal("100"), "KRW", at(1));
        cache.update(PRODUCT_ID, TRADE_DATE, new BigDecimal("120"), "KRW", at(2));
        cache.update(PRODUCT_ID, TRADE_DATE, new BigDecimal("90"), "KRW", at(3));
        cache.update(PRODUCT_ID, TRADE_DATE, new BigDecimal("110"), "KRW", at(4));

        IntradayCandle candle = cache.find(PRODUCT_ID);

        assertEquals(new BigDecimal("100"), candle.getOpenPrice());
        assertEquals(new BigDecimal("120"), candle.getHighPrice());
        assertEquals(new BigDecimal("90"), candle.getLowPrice());
        assertEquals(new BigDecimal("110"), candle.getClosePrice());
    }

    @Test
    @DisplayName("토스 일봉으로 실제 시가를 교정하되 더 최신 현재가는 보존한다")
    void seedsOfficialCandleWithoutLosingNewerTick() {
        IntradayCandleCache cache = new IntradayCandleCache();
        cache.update(PRODUCT_ID, TRADE_DATE, new BigDecimal("115"), "KRW", at(10));

        ProductDailyCandle official = new ProductDailyCandle();
        official.setProductId(PRODUCT_ID);
        official.setTradeDate(TRADE_DATE);
        official.setOpenPrice(new BigDecimal("100"));
        official.setHighPrice(new BigDecimal("112"));
        official.setLowPrice(new BigDecimal("95"));
        official.setClosePrice(new BigDecimal("108"));
        official.setVolume(new BigDecimal("1000"));
        official.setCurrency("KRW");
        official.setUpdatedAt(at(5));

        cache.seed(official);

        IntradayCandle candle = cache.find(PRODUCT_ID);

        assertEquals(new BigDecimal("100"), candle.getOpenPrice());
        assertEquals(new BigDecimal("115"), candle.getHighPrice());
        assertEquals(new BigDecimal("95"), candle.getLowPrice());
        assertEquals(new BigDecimal("115"), candle.getClosePrice());
        assertEquals(new BigDecimal("1000"), candle.getVolume());
    }

    private static LocalDateTime at(int second) {
        return LocalDateTime.of(2026, 8, 21, 4, 0, second);
    }
}
