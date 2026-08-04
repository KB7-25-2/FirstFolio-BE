package org.firstfolio.simulation.service.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.simulation.client.finlife.FinlifeClient;
import org.firstfolio.simulation.client.finlife.FinlifeProduct;
import org.firstfolio.simulation.client.finlife.FinlifeProductType;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.firstfolio.simulation.service.TimeCompressionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepositSavingCollectorTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 4, 0, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 7, 18);

    private final TermsJsonCodec termsJsonCodec = new TermsJsonCodec();

    private FinlifeClient finlifeClient;
    private DepositSavingCollector collector;

    @BeforeEach
    void setUp() {
        finlifeClient = mock(FinlifeClient.class);
        collector = new DepositSavingCollector(
                finlifeClient,
                new TimeCompressionPolicy(24),
                termsJsonCodec
        );

        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT)).thenReturn(List.of());
        when(finlifeClient.fetchAll(FinlifeProductType.SAVING)).thenReturn(List.of());
    }

    private static FinlifeProduct deposit(int months, String rate) {
        return new FinlifeProduct(
                FinlifeProductType.DEPOSIT, "0010927", "010300100335", months,
                "국민은행", "KB Star 정기예금", new BigDecimal(rate), "단리", null, "202607"
        );
    }

    private List<FinancialProduct> collectDeposits(FinlifeProduct... products) {
        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT)).thenReturn(List.of(products));

        return collector.collect(REFERENCE_AT, NOW);
    }

    @Test
    @DisplayName("제공처 식별자는 FSS_FINLIFE다")
    void exposesSourceProvider() {
        assertEquals("FSS_FINLIFE", collector.sourceProvider());
    }

    @Test
    @DisplayName("수집한 상품을 비공개 상태로 만든다 — 관리자 검토 전에는 공개하지 않는다")
    void producesInactiveProducts() {
        FinancialProduct product = collectDeposits(deposit(6, "2.35")).get(0);

        assertFalse(product.isActive(), "검토 전 상품은 비공개여야 합니다.");
        assertEquals(AssetType.DEPOSIT_SAVINGS, product.getAssetType());
        assertEquals("LOW", product.getRiskLevel());
    }

    @Test
    @DisplayName("원상품 식별 코드에 회사·상품·만기·이자방식·적립유형을 모두 담는다")
    void buildsCompositeSourceCode() {
        FinancialProduct product = collectDeposits(deposit(6, "2.35")).get(0);

        assertEquals("0010927:010300100335:6:단리:", product.getSourceProductCode());
        assertEquals("국민은행 KB Star 정기예금 6개월 단리", product.getSourceProductName());
    }

    @Test
    @DisplayName("실제 만기에 압축 배율을 적용한다 — 만기일시지급이라 지급 주기가 만기와 같다")
    void appliesTimeCompression() {
        JsonNode terms = termsJsonCodec.read(
                collectDeposits(deposit(6, "2.35")).get(0).getSimulationTermsJson()
        );

        assertEquals(144, terms.get("service_maturity_hours").asInt());
        assertEquals(144, terms.get("service_interest_interval_hours").asInt());
        assertEquals(24, terms.get("compression_hours_per_month").asInt());
    }

    @Test
    @DisplayName("가져온 금리를 그대로 쓰고, 출처가 없는 이자 주기는 가정치로 표시한다")
    void keepsSourceRateAndFlagsAssumedInterval() {
        JsonNode terms = termsJsonCodec.read(
                collectDeposits(deposit(6, "2.35")).get(0).getRealTermsJson()
        );

        assertEquals("2.35", terms.get("interest_rate").asText());
        assertEquals(6, terms.get("maturity_months").asInt());
        assertEquals("SIMPLE", terms.get("interest_rate_type").asText());
        assertEquals("MATURITY", terms.get("interest_interval").asText());
        assertEquals("ASSUMED", terms.get("interest_interval_source").asText());
    }

    @Test
    @DisplayName("만기를 알 수 없는 항목은 임의로 만들지 않고 제외한다")
    void skipsProductsWithoutMaturity() {
        assertEquals(1, collectDeposits(deposit(0, "2.35"), deposit(6, "2.35")).size());
    }

    @Test
    @DisplayName("같은 상품·만기라도 이자 방식이 다르면 다른 상품으로 등록한다")
    void distinguishesProductsThatDifferOnlyByRateType() {
        FinlifeProduct simple = new FinlifeProduct(
                FinlifeProductType.DEPOSIT, "0013127", "240000", 12,
                "KB저축은행", "정기예금", new BigDecimal("3.5"), "단리", null, "202607"
        );
        FinlifeProduct compound = new FinlifeProduct(
                FinlifeProductType.DEPOSIT, "0013127", "240000", 12,
                "KB저축은행", "정기예금", new BigDecimal("3.5"), "복리", null, "202607"
        );

        List<FinancialProduct> products = collectDeposits(simple, compound);

        assertEquals(2, products.size(), "이자 방식이 다르면 별개 상품이어야 합니다.");
        assertNotEquals(
                products.get(0).getSourceProductCode(),
                products.get(1).getSourceProductCode(),
                "식별 코드가 겹치면 한쪽이 조용히 사라집니다."
        );
    }

    @Test
    @DisplayName("같은 상품·만기라도 적립 유형이 다르면 다른 상품으로 등록한다")
    void distinguishesProductsThatDifferOnlyByReserveType() {
        FinlifeProduct fixed = new FinlifeProduct(
                FinlifeProductType.SAVING, "0010927", "S001", 12,
                "국민은행", "적금", new BigDecimal("2.55"), "단리", "정액적립식", "202607"
        );
        FinlifeProduct flexible = new FinlifeProduct(
                FinlifeProductType.SAVING, "0010927", "S001", 12,
                "국민은행", "적금", new BigDecimal("2.35"), "단리", "자유적립식", "202607"
        );

        when(finlifeClient.fetchAll(FinlifeProductType.SAVING))
                .thenReturn(List.of(fixed, flexible));

        List<FinancialProduct> products = collector.collect(REFERENCE_AT, NOW);

        assertEquals(2, products.size());
        assertNotEquals(
                products.get(0).getSourceProductCode(),
                products.get(1).getSourceProductCode()
        );
    }
}
