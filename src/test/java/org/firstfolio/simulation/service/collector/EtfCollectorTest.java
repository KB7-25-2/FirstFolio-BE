package org.firstfolio.simulation.service.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.simulation.client.datago.EtfPriceClient;
import org.firstfolio.simulation.client.datago.EtfPriceResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.EtfCatalog;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EtfCollectorTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 4, 0, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 9, 0);

    private final TermsJsonCodec termsJsonCodec = new TermsJsonCodec();

    private EtfPriceClient client;
    private EtfCollector collector;

    @BeforeEach
    void setUp() {
        client = mock(EtfPriceClient.class);
        collector = new EtfCollector(client, termsJsonCodec);
    }

    private static EtfPriceResponse.Item quote(String srtnCd, String close) {
        EtfPriceResponse.Item item = new EtfPriceResponse.Item();

        item.setSrtnCd(srtnCd);
        item.setClpr(close == null ? null : new BigDecimal(close));
        item.setItmsNm("이름");
        item.setBssIdxIdxNm("코스피 200");

        return item;
    }

    private void givenAllTradable() {
        List<EtfPriceResponse.Item> quotes = new ArrayList<>();

        for (EtfCatalog etf : EtfCatalog.all()) {
            quotes.add(quote(etf.getShortCode(), "99105"));
        }

        when(client.fetchLatest(anyList(), any(LocalDate.class))).thenReturn(quotes);
    }

    @Test
    @DisplayName("제공처 식별자는 DATA_GO_KR_ETF다")
    void exposesSourceProvider() {
        assertEquals("DATA_GO_KR_ETF", collector.sourceProvider());
    }

    @Test
    @DisplayName("시세가 확인된 5종목을 비공개로 등록한다")
    void registersAllTradableEtfs() {
        givenAllTradable();

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        assertEquals(5, products.size());
        products.forEach(p -> {
            assertFalse(p.isActive());
            assertEquals(AssetType.FUND, p.getAssetType());
        });
    }

    @Test
    @DisplayName("ETF도 압축하지 않고, 주식과 구분되는 사유를 남긴다")
    void marksEtfAsNotTimeCompressed() {
        givenAllTradable();

        JsonNode terms = termsJsonCodec.read(
                collector.collect(REFERENCE_AT, NOW).get(0).getSimulationTermsJson()
        );

        assertFalse(terms.get("time_compressed").asBoolean());
        assertEquals("ETF_REALTIME_PRICE", terms.get("reason").asText());
    }

    @Test
    @DisplayName("v3 1절 구성대로 주식형 2 / 채권형 2 / 혼합형 1이다")
    void followsPolicyFundComposition() {
        givenAllTradable();

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        long equity = countByFundType(products, "EQUITY");
        long bond = countByFundType(products, "BOND");
        long mixed = countByFundType(products, "MIXED");

        assertEquals(2, equity);
        assertEquals(2, bond);
        assertEquals(1, mixed);
    }

    private long countByFundType(List<FinancialProduct> products, String type) {
        return products.stream()
                .filter(p -> type.equals(
                        termsJsonCodec.read(p.getRealTermsJson()).get("fund_type").asText()))
                .count();
    }

    @Test
    @DisplayName("주식형은 HIGH, 채권형·혼합형은 MEDIUM이다")
    void assignsRiskByFundType() {
        givenAllTradable();

        for (FinancialProduct product : collector.collect(REFERENCE_AT, NOW)) {
            String fundType = termsJsonCodec.read(product.getRealTermsJson())
                    .get("fund_type").asText();

            assertEquals(
                    "EQUITY".equals(fundType) ? "HIGH" : "MEDIUM",
                    product.getRiskLevel(),
                    fundType + "의 위험도가 다릅니다."
            );
        }
    }

    @Test
    @DisplayName("실제 조건에 종목명·종목코드·기초지수를 담지 않는다")
    void doesNotLeakIdentityIntoRealTerms() {
        givenAllTradable();

        for (FinancialProduct product : collector.collect(REFERENCE_AT, NOW)) {
            String realTerms = product.getRealTermsJson();

            assertFalse(realTerms.contains(product.getSourceProductName()),
                    "종목명이 real_terms에 남으면 안 됩니다: " + realTerms);
            assertFalse(realTerms.contains(product.getSourceProductCode()),
                    "종목코드가 real_terms에 남으면 안 됩니다: " + realTerms);
            assertFalse(realTerms.contains("코스피"),
                    "기초지수명이 real_terms에 남으면 안 됩니다: " + realTerms);
        }
    }

    @Test
    @DisplayName("시세가 없는 종목은 등록하지 않는다")
    void skipsEtfsWithoutQuote() {
        when(client.fetchLatest(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(quote("069500", "99105"), quote("229200", null)));

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        assertEquals(1, products.size());
        assertEquals("069500", products.get(0).getSourceProductCode());
    }
}
