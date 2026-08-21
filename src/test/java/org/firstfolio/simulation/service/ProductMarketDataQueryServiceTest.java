package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.dto.response.ProductMarketSnapshotResponse;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.mapper.ProductDailyCandleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMarketDataQueryServiceTest {

    private static final long PRODUCT_ID = 87L;

    private FinancialProductMapper productMapper;
    private ProductDailyCandleMapper candleMapper;
    private CurrentPriceReader priceReader;
    private IntradayCandleCache intradayCache;
    private ProductMarketDataQueryService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(FinancialProductMapper.class);
        candleMapper = mock(ProductDailyCandleMapper.class);
        priceReader = mock(CurrentPriceReader.class);
        intradayCache = new IntradayCandleCache();
        service = new ProductMarketDataQueryService(
                productMapper,
                candleMapper,
                priceReader,
                intradayCache,
                new TradingHours(),
                Clock.fixed(Instant.parse("2026-08-21T04:00:00Z"), ZoneOffset.UTC)
        );

        when(productMapper.findActiveById(PRODUCT_ID)).thenReturn(product(AssetType.STOCK));
    }

    @Test
    @DisplayName("count를 생략하면 확정 일봉 200개를 요청한다")
    void defaultsToTwoHundredCandles() {
        when(candleMapper.findRecentByProductId(PRODUCT_ID, 200)).thenReturn(List.of());

        service.findCandles(PRODUCT_ID, null);

        verify(candleMapper).findRecentByProductId(PRODUCT_ID, 200);
    }

    @Test
    @DisplayName("장중 스냅샷은 현재가와 메모리 OHLC를 잠정 상태로 반환한다")
    void returnsProvisionalIntradayCandle() {
        ProductPrice current = new ProductPrice();
        current.setProductId(PRODUCT_ID);
        current.setPrice(new BigDecimal("281500.0000"));
        current.setReferenceAt(LocalDateTime.of(2026, 8, 21, 4, 0));
        when(priceReader.read(PRODUCT_ID)).thenReturn(current);

        intradayCache.update(
                PRODUCT_ID,
                LocalDate.of(2026, 8, 21),
                new BigDecimal("281500.0000"),
                "KRW",
                LocalDateTime.of(2026, 8, 21, 4, 0)
        );

        ProductMarketSnapshotResponse response = service.findMarketSnapshot(PRODUCT_ID);

        assertEquals(new BigDecimal("281500.0000"), response.getCurrentPrice());
        assertEquals("PROVISIONAL", response.getCurrentCandle().getStatus());
        assertEquals(true, response.isMarketOpen());
    }

    @Test
    @DisplayName("일봉 건수는 1~200만 허용한다")
    void validatesCount() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findCandles(PRODUCT_ID, 201)
        );

        assertEquals(ErrorCode.INVALID_CANDLE_QUERY, exception.getErrorCode());
    }

    @Test
    @DisplayName("예적금·채권 상품에는 차트 API를 열지 않는다")
    void rejectsUnsupportedAsset() {
        when(productMapper.findActiveById(PRODUCT_ID)).thenReturn(product(AssetType.BOND));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findCandles(PRODUCT_ID, 10)
        );

        assertEquals(ErrorCode.PRODUCT_CANDLE_NOT_SUPPORTED, exception.getErrorCode());
    }

    private static FinancialProduct product(AssetType assetType) {
        FinancialProduct product = new FinancialProduct();
        product.setProductId(PRODUCT_ID);
        product.setAssetType(assetType);
        product.setActive(true);
        return product;
    }
}
