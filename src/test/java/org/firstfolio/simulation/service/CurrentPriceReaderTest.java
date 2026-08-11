package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentPriceReaderTest {

    private static final long STOCK_ID = 87L;
    private static final long FUND_ID = 88L;

    private PriceCache cache;
    private ProductPriceMapper mapper;
    private CurrentPriceReader reader;

    @BeforeEach
    void setUp() {
        cache = new PriceCache();
        mapper = mock(ProductPriceMapper.class);
        reader = new CurrentPriceReader(cache, mapper);
    }

    private static ProductPrice price(Long productId, String value) {
        ProductPrice price = new ProductPrice();

        price.setProductId(productId);
        price.setPrice(new BigDecimal(value));

        return price;
    }

    // ------------------------------------------------------------- 단건

    @Test
    @DisplayName("캐시에 있으면 DB를 아예 부르지 않는다")
    void skipsDatabaseOnCacheHit() {
        cache.put(price(STOCK_ID, "241500.0000"));

        assertEquals(new BigDecimal("241500.0000"), reader.read(STOCK_ID).getPrice());
        verify(mapper, never()).findLatestByProductId(anyLong());
    }

    @Test
    @DisplayName("캐시에 없으면 DB 종가로 넘어간다 — 재시작 직후·장외의 정상 경로")
    void fallsBackToDatabaseOnCacheMiss() {
        when(mapper.findLatestByProductId(STOCK_ID)).thenReturn(price(STOCK_ID, "240000.0000"));

        assertEquals(new BigDecimal("240000.0000"), reader.read(STOCK_ID).getPrice());
        verify(mapper).findLatestByProductId(STOCK_ID);
    }

    @Test
    @DisplayName("캐시가 DB보다 우선한다 — 장중에는 실시간 값으로 체결·평가한다")
    void prefersCacheOverDatabase() {
        cache.put(price(STOCK_ID, "241500.0000"));
        when(mapper.findLatestByProductId(STOCK_ID)).thenReturn(price(STOCK_ID, "240000.0000"));

        assertEquals(new BigDecimal("241500.0000"), reader.read(STOCK_ID).getPrice());
    }

    @Test
    @DisplayName("캐시에도 DB에도 없으면 null이다 — 없는 가격을 만들지 않는다")
    void returnsNullWhenNowhere() {
        when(mapper.findLatestByProductId(STOCK_ID)).thenReturn(null);

        assertNull(reader.read(STOCK_ID));
    }

    // ------------------------------------------------------------- 벌크

    @Test
    @DisplayName("전부 캐시에 있으면 DB를 부르지 않는다")
    void bulkSkipsDatabaseWhenFullyCached() {
        cache.putAll(List.of(price(STOCK_ID, "241500.0000"), price(FUND_ID, "72300.0000")));

        Map<Long, ProductPrice> found = reader.readAll(List.of(STOCK_ID, FUND_ID));

        assertEquals(2, found.size());
        verify(mapper, never()).findLatestByProductIds(any());
    }

    @Test
    @DisplayName("일부만 캐시에 있으면 빠진 것만 DB에서 읽는다")
    void bulkQueriesOnlyMissingProducts() {
        cache.put(price(STOCK_ID, "241500.0000"));
        when(mapper.findLatestByProductIds(List.of(FUND_ID)))
                .thenReturn(List.of(price(FUND_ID, "72300.0000")));

        Map<Long, ProductPrice> found = reader.readAll(List.of(STOCK_ID, FUND_ID));

        assertEquals(2, found.size());
        assertEquals(new BigDecimal("241500.0000"), found.get(STOCK_ID).getPrice());
        assertEquals(new BigDecimal("72300.0000"), found.get(FUND_ID).getPrice());

        // 캐시에 있던 87을 다시 묻지 않는 것이 핵심이다.
        verify(mapper).findLatestByProductIds(List.of(FUND_ID));
    }

    @Test
    @DisplayName("빈 목록이면 DB를 부르지 않는다 — IN ()이 만들어져 SQL이 깨진다")
    void bulkSkipsDatabaseOnEmptyInput() {
        assertTrue(reader.readAll(List.of()).isEmpty());
        assertTrue(reader.readAll(null).isEmpty());

        verify(mapper, never()).findLatestByProductIds(any());
    }

    @Test
    @DisplayName("어디에도 없는 상품은 결과에서 빠진다")
    void bulkOmitsUnknownProducts() {
        when(mapper.findLatestByProductIds(List.of(STOCK_ID, FUND_ID))).thenReturn(List.of());

        assertTrue(reader.readAll(List.of(STOCK_ID, FUND_ID)).isEmpty());
    }

    @Test
    @DisplayName("같은 상품이 두 번 들어와도 DB 질의는 한 번이다")
    void bulkDeduplicatesProductIds() {
        when(mapper.findLatestByProductIds(List.of(STOCK_ID)))
                .thenReturn(List.of(price(STOCK_ID, "240000.0000")));

        Map<Long, ProductPrice> found = reader.readAll(List.of(STOCK_ID, STOCK_ID));

        assertEquals(1, found.size());
        verify(mapper).findLatestByProductIds(List.of(STOCK_ID));
    }

    @Test
    @DisplayName("단건과 벌크가 같은 값을 준다 — 갈라지면 평가액과 체결가가 어긋난다")
    void singleAndBulkAgree() {
        cache.put(price(STOCK_ID, "241500.0000"));
        when(mapper.findLatestByProductId(FUND_ID)).thenReturn(price(FUND_ID, "72300.0000"));
        when(mapper.findLatestByProductIds(List.of(FUND_ID)))
                .thenReturn(List.of(price(FUND_ID, "72300.0000")));

        Map<Long, ProductPrice> bulk = reader.readAll(List.of(STOCK_ID, FUND_ID));

        assertEquals(reader.read(STOCK_ID).getPrice(), bulk.get(STOCK_ID).getPrice(), "캐시 히트");
        assertEquals(reader.read(FUND_ID).getPrice(), bulk.get(FUND_ID).getPrice(), "DB 폴백");
    }
}
