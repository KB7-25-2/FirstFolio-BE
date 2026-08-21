package org.firstfolio.simulation.service;

import org.firstfolio.simulation.client.toss.TossCandlesResponse;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.firstfolio.simulation.mapper.ProductDailyCandleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCandleSyncServiceTest {

    private static final long PRODUCT_ID = 87L;

    private PriceQuoteFetcher quoteFetcher;
    private TossInvestClient tossClient;
    private ProductDailyCandleMapper candleMapper;
    private IntradayCandleCache intradayCache;
    private ProductCandleSyncService service;

    @BeforeEach
    void setUp() {
        quoteFetcher = mock(PriceQuoteFetcher.class);
        tossClient = mock(TossInvestClient.class);
        candleMapper = mock(ProductDailyCandleMapper.class);
        intradayCache = new IntradayCandleCache();
        service = new ProductCandleSyncService(
                quoteFetcher,
                tossClient,
                candleMapper,
                intradayCache,
                new TradingHours()
        );

        FinancialProduct product = new FinancialProduct();
        product.setProductId(PRODUCT_ID);
        product.setAssetType(AssetType.STOCK);
        product.setSourceProductCode("005930");

        when(quoteFetcher.findTargets(null)).thenReturn(List.of(product));
        when(tossClient.fetchDailyCandles(anyString(), anyInt())).thenReturn(List.of(
                candle("2026-08-21T00:00:00.000+09:00", "273000", "285000", "266000", "281500"),
                candle("2026-08-20T00:00:00.000+09:00", "258000", "274000", "252000", "273000")
        ));
    }

    @Test
    @DisplayName("장중에는 오늘 봉을 메모리에만 두고 전날까지 확정 저장한다")
    void excludesCurrentSessionFromDatabase() {
        LocalDateTime duringSessionUtc = LocalDateTime.of(2026, 8, 21, 4, 0);

        service.syncRecentConfirmed(duringSessionUtc);

        ArgumentCaptor<List<ProductDailyCandle>> saved = ArgumentCaptor.forClass(List.class);
        verify(candleMapper).upsertAll(saved.capture());

        assertEquals(1, saved.getValue().size());
        assertEquals("2026-08-20", saved.getValue().get(0).getTradeDate().toString());
        assertNotNull(intradayCache.find(PRODUCT_ID));
        assertEquals(new BigDecimal("273000.0000"), intradayCache.find(PRODUCT_ID).getOpenPrice());
    }

    @Test
    @DisplayName("장 마감 후에는 오늘 봉도 확정 일봉으로 upsert한다")
    void includesCurrentSessionAfterClose() {
        LocalDateTime afterCloseUtc = LocalDateTime.of(2026, 8, 21, 7, 0);

        service.syncRecentConfirmed(afterCloseUtc);

        ArgumentCaptor<List<ProductDailyCandle>> saved = ArgumentCaptor.forClass(List.class);
        verify(candleMapper).upsertAll(saved.capture());

        assertEquals(2, saved.getValue().size());
        assertEquals("2026-08-21", saved.getValue().get(0).getTradeDate().toString());
    }

    @Test
    @DisplayName("장중 공식 일봉 교정은 DB를 쓰지 않는다")
    void intradayRefreshDoesNotPersist() {
        service.refreshIntraday(LocalDateTime.of(2026, 8, 21, 4, 0));

        verify(candleMapper, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
        assertNotNull(intradayCache.find(PRODUCT_ID));
    }

    private static TossCandlesResponse.Item candle(
            String timestamp,
            String open,
            String high,
            String low,
            String close
    ) {
        TossCandlesResponse.Item item = new TossCandlesResponse.Item();
        item.setTimestamp(timestamp);
        item.setOpenPrice(new BigDecimal(open));
        item.setHighPrice(new BigDecimal(high));
        item.setLowPrice(new BigDecimal(low));
        item.setClosePrice(new BigDecimal(close));
        item.setVolume(new BigDecimal("1000"));
        item.setCurrency("KRW");
        return item;
    }
}
