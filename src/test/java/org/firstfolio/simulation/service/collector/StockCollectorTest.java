package org.firstfolio.simulation.service.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.KrxStock;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockCollectorTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 4, 0, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 8, 43);

    private final TermsJsonCodec termsJsonCodec = new TermsJsonCodec();

    private TossInvestClient client;
    private StockCollector collector;

    @BeforeEach
    void setUp() {
        client = mock(TossInvestClient.class);
        collector = new StockCollector(client, termsJsonCodec);
    }

    private static TossPricesResponse.Item quote(String symbol, String price) {
        TossPricesResponse.Item item = new TossPricesResponse.Item();

        item.setSymbol(symbol);
        item.setLastPrice(price == null ? null : new BigDecimal(price));
        item.setCurrency("KRW");
        item.setTimestamp("2026-08-04T17:43:44.000+09:00");

        return item;
    }

    private void givenAllTradable() {
        List<TossPricesResponse.Item> quotes = new ArrayList<>();

        for (KrxStock stock : KrxStock.all()) {
            quotes.add(quote(stock.getSymbol(), "100000"));
        }

        when(client.fetchPrices(anyList())).thenReturn(quotes);
    }

    @Test
    @DisplayName("제공처 식별자는 TOSSINVEST다")
    void exposesSourceProvider() {
        assertEquals("TOSSINVEST", collector.sourceProvider());
    }

    @Test
    @DisplayName("시세가 확인된 10종목을 비공개로 등록한다")
    void registersAllTradableStocks() {
        givenAllTradable();

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        assertEquals(10, products.size());
        products.forEach(p -> {
            assertFalse(p.isActive(), "검토 전 상품은 비공개여야 합니다.");
            assertEquals(AssetType.STOCK, p.getAssetType());
            assertEquals("HIGH", p.getRiskLevel());
        });
    }

    @Test
    @DisplayName("시세가 없는 종목은 등록하지 않는다 — 잘못된 종목코드가 조용히 들어가면 안 된다")
    void skipsSymbolsWithoutQuote() {
        // 토스는 없는 종목코드를 오류 없이 결과에서 빼버린다.
        when(client.fetchPrices(anyList()))
                .thenReturn(List.of(quote("005930", "241500"), quote("000660", "1596000")));

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        assertEquals(2, products.size());
        assertTrue(
                products.stream().allMatch(p ->
                        List.of("005930", "000660").contains(p.getSourceProductCode())),
                "시세가 확인된 종목만 등록해야 합니다."
        );
    }

    @Test
    @DisplayName("가격이 null인 종목도 제외한다")
    void skipsSymbolsWithNullPrice() {
        when(client.fetchPrices(anyList()))
                .thenReturn(List.of(quote("005930", "241500"), quote("000660", null)));

        assertEquals(1, collector.collect(REFERENCE_AT, NOW).size());
    }

    @Test
    @DisplayName("주식은 압축하지 않고, 그 사실을 데이터에 남긴다")
    void marksStocksAsNotTimeCompressed() {
        givenAllTradable();

        JsonNode terms = termsJsonCodec.read(
                collector.collect(REFERENCE_AT, NOW).get(0).getSimulationTermsJson()
        );

        assertFalse(terms.get("time_compressed").asBoolean());
        assertEquals("STOCK_REALTIME_PRICE", terms.get("reason").asText());
    }

    @Test
    @DisplayName("실제 조건에 시장 구분과 산업군을 담는다")
    void keepsMarketAndSector() {
        givenAllTradable();

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        JsonNode kospi = termsJsonCodec.read(
                products.stream().filter(p -> "005930".equals(p.getSourceProductCode()))
                        .findFirst().orElseThrow().getRealTermsJson()
        );
        JsonNode kosdaq = termsJsonCodec.read(
                products.stream().filter(p -> "035900".equals(p.getSourceProductCode()))
                        .findFirst().orElseThrow().getRealTermsJson()
        );

        assertEquals("KOSPI", kospi.get("market").asText());
        assertEquals("반도체", kospi.get("sector").asText());
        // JYP만 코스닥이다 (v3 2.2절).
        assertEquals("KOSDAQ", kosdaq.get("market").asText());
        assertEquals("엔터", kosdaq.get("sector").asText());
    }

    @Test
    @DisplayName("실제 조건에 종목명·종목코드를 담지 않는다 — 사용자 응답에 실리는 값이다")
    void doesNotLeakStockIdentityIntoRealTerms() {
        givenAllTradable();

        for (FinancialProduct product : collector.collect(REFERENCE_AT, NOW)) {
            String realTerms = product.getRealTermsJson();

            assertFalse(
                    realTerms.contains(product.getSourceProductName()),
                    "종목명이 real_terms에 남으면 안 됩니다: " + realTerms
            );
            assertFalse(
                    realTerms.contains(product.getSourceProductCode()),
                    "종목코드가 real_terms에 남으면 안 됩니다: " + realTerms
            );
        }
    }

    @Test
    @DisplayName("v3 2.2절의 10종목 구성을 그대로 따른다")
    void followsPolicyStockList() {
        assertEquals(10, KrxStock.all().size());
        assertEquals(
                5,
                KrxStock.all().stream().map(KrxStock::getSector).distinct().count(),
                "반도체·방산·제약·엔터·조선 5개 산업군이어야 합니다."
        );
        assertEquals(
                1,
                KrxStock.all().stream().filter(s -> "KOSDAQ".equals(s.getMarket())).count(),
                "코스닥은 JYP 하나입니다."
            );
    }
}
