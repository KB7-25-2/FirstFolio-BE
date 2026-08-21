package org.firstfolio.simulation.controller;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.dto.response.ProductCandleHistoryResponse;
import org.firstfolio.simulation.dto.response.ProductMarketSnapshotResponse;
import org.firstfolio.simulation.service.FinancialProductQueryService;
import org.firstfolio.simulation.service.ProductMarketDataQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialProductMarketDataControllerTest {

    private ProductMarketDataQueryService marketDataService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        marketDataService = mock(ProductMarketDataQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FinancialProductController(
                        mock(FinancialProductQueryService.class),
                        marketDataService
                ))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    @DisplayName("확정 일봉을 차트 순서와 snake_case 필드로 반환한다")
    void returnsDailyCandles() throws Exception {
        ProductDailyCandle candle = new ProductDailyCandle();
        candle.setTradeDate(LocalDate.of(2026, 8, 20));
        candle.setOpenPrice(new BigDecimal("258000.0000"));
        candle.setHighPrice(new BigDecimal("274000.0000"));
        candle.setLowPrice(new BigDecimal("252000.0000"));
        candle.setClosePrice(new BigDecimal("273000.0000"));
        candle.setVolume(new BigDecimal("46036748.00000000"));
        candle.setCurrency("KRW");

        when(marketDataService.findCandles(87L, 5))
                .thenReturn(new ProductCandleHistoryResponse(87L, List.of(candle)));

        mockMvc.perform(get("/api/financial-products/87/candles").param("count", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product_id").value(87))
                .andExpect(jsonPath("$.data.interval").value("1d"))
                .andExpect(jsonPath("$.data.candles[0].trade_date").value("2026-08-20"))
                .andExpect(jsonPath("$.data.candles[0].open_price").value("258000.0000"))
                .andExpect(jsonPath("$.data.candles[0].volume").value("46036748.00000000"));
    }

    @Test
    @DisplayName("2초 폴링 응답에 현재가와 당일 OHLC를 함께 반환한다")
    void returnsMarketSnapshot() throws Exception {
        ProductPrice current = new ProductPrice();
        current.setProductId(87L);
        current.setPrice(new BigDecimal("281500.0000"));
        current.setReferenceAt(LocalDateTime.of(2026, 8, 21, 4, 0));

        ProductDailyCandle candle = new ProductDailyCandle();
        candle.setTradeDate(LocalDate.of(2026, 8, 21));
        candle.setOpenPrice(new BigDecimal("273000.0000"));
        candle.setHighPrice(new BigDecimal("285000.0000"));
        candle.setLowPrice(new BigDecimal("266000.0000"));
        candle.setClosePrice(new BigDecimal("281500.0000"));

        ProductMarketSnapshotResponse response = new ProductMarketSnapshotResponse(
                87L,
                current,
                true,
                ProductMarketSnapshotResponse.confirmed(candle, current.getReferenceAt())
        );
        when(marketDataService.findMarketSnapshot(87L)).thenReturn(response);

        mockMvc.perform(get("/api/financial-products/87/market-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_price").value("281500.0000"))
                .andExpect(jsonPath("$.data.market_open").value(true))
                .andExpect(jsonPath("$.data.current_candle.open_price").value("273000.0000"))
                .andExpect(jsonPath("$.data.current_candle.status").value("CONFIRMED"));

        verify(marketDataService).findMarketSnapshot(87L);
    }
}
