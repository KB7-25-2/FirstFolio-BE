package org.firstfolio.simulation.service.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.simulation.client.datago.BondBasicInfo;
import org.firstfolio.simulation.client.datago.BondIssueInfoClient;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.firstfolio.simulation.service.TimeCompressionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BondCollectorTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 4, 0, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 7, 18);

    private final TermsJsonCodec termsJsonCodec = new TermsJsonCodec();

    private BondIssueInfoClient client;
    private BondCollector collector;

    @BeforeEach
    void setUp() {
        client = mock(BondIssueInfoClient.class);
        collector = new BondCollector(
                client,
                new TimeCompressionPolicy(24),
                termsJsonCodec,
                "KR6003492DB8,KR103501GF30"
        );
    }

    /** 대한항공 105-2 — A0 회사채, 만기 2026-11-10, 표면 5.397%, 3개월 이표 */
    private static BondBasicInfo corporateBond() {
        return new BondBasicInfo(
                "KR6003492DB8", "대한항공 105-2", "대한항공",
                LocalDate.of(2023, 11, 10), LocalDate.of(2026, 11, 10),
                new BigDecimal("5.397"), 3, "이표채", "일반회사채", "A0"
        );
    }

    /** 국고채권 — 신용등급 없음, 만기 2027-03-10, 6개월 이표 */
    private static BondBasicInfo governmentBond() {
        return new BondBasicInfo(
                "KR103501GF30", "국고채권 02625-2703(25-1)", "대한민국",
                LocalDate.of(2025, 3, 10), LocalDate.of(2027, 3, 10),
                new BigDecimal("2.625"), 6, "이표채", "국채", null
        );
    }

    private List<FinancialProduct> collect(BondBasicInfo... bonds) {
        when(client.fetchLatest(anyList(), any(LocalDate.class))).thenReturn(List.of(bonds));

        return collector.collect(REFERENCE_AT, NOW);
    }

    @Test
    @DisplayName("제공처 식별자는 DATA_GO_KR_BOND다")
    void exposesSourceProvider() {
        assertEquals("DATA_GO_KR_BOND", collector.sourceProvider());
    }

    @Test
    @DisplayName("수집한 채권을 비공개 상태로 만든다")
    void producesInactiveProducts() {
        FinancialProduct product = collect(corporateBond()).get(0);

        assertFalse(product.isActive());
        assertEquals(AssetType.BOND, product.getAssetType());
        assertEquals("KR6003492DB8", product.getSourceProductCode());
        assertEquals("대한항공 105-2", product.getSourceProductName());
    }

    @Test
    @DisplayName("잔존 만기를 압축한다 — 원래 만기(36개월)가 아니라 남은 3개월 기준")
    void compressesRemainingMaturityNotOriginalTerm() {
        JsonNode terms = termsJsonCodec.read(
                collect(corporateBond()).get(0).getSimulationTermsJson()
        );

        // 2026-08-04 → 2026-11-10 = 3개월(내림) → 3일 = 72시간
        assertEquals(72, terms.get("service_maturity_hours").asInt());
    }

    @Test
    @DisplayName("이표채는 이자 지급 주기를 만기와 별도로 압축한다")
    void compressesInterestIntervalSeparately() {
        JsonNode terms = termsJsonCodec.read(
                collect(governmentBond()).get(0).getSimulationTermsJson()
        );

        // 2026-08-04 → 2027-03-10 = 7개월 → 7일 = 168시간, 이자 주기 6개월 → 6일 = 144시간
        assertEquals(168, terms.get("service_maturity_hours").asInt());
        assertEquals(144, terms.get("service_interest_interval_hours").asInt());
    }

    @Test
    @DisplayName("실제 조건에 발행사·종목코드를 담지 않는다 — 사용자 응답에 실리는 값이다")
    void doesNotLeakIssuerIntoRealTerms() {
        String realTerms = collect(corporateBond()).get(0).getRealTermsJson();

        assertFalse(realTerms.contains("대한항공"), "발행사명이 real_terms에 남으면 안 됩니다.");
        assertFalse(realTerms.contains("KR6003492DB8"), "종목코드가 real_terms에 남으면 안 됩니다.");
        assertFalse(realTerms.contains("105-2"), "종목명이 real_terms에 남으면 안 됩니다.");
    }

    @Test
    @DisplayName("표면이율·신용등급·채권 종류는 실제 조건에 담는다 — 학습 대상이다")
    void keepsEducationalFields() {
        JsonNode terms = termsJsonCodec.read(collect(corporateBond()).get(0).getRealTermsJson());

        assertEquals("5.397", terms.get("coupon_rate").asText());
        assertEquals(3, terms.get("maturity_months").asInt());
        assertEquals(3, terms.get("interest_interval_months").asInt());
        assertEquals("A0", terms.get("credit_rating").asText());
        assertEquals("일반회사채", terms.get("bond_category").asText());
    }

    @Test
    @DisplayName("국채는 신용등급이 없고 위험도가 LOW다")
    void governmentBondHasNoRatingAndLowRisk() {
        FinancialProduct product = collect(governmentBond()).get(0);
        JsonNode terms = termsJsonCodec.read(product.getRealTermsJson());

        assertEquals("LOW", product.getRiskLevel());
        assertNull(terms.get("credit_rating"), "국채는 등급 필드를 생략합니다.");
    }

    @Test
    @DisplayName("회사채는 위험도가 MEDIUM이다")
    void corporateBondHasMediumRisk() {
        assertEquals("MEDIUM", collect(corporateBond()).get(0).getRiskLevel());
    }

    /** 개인투자용국채(복리) — 만기 2029-04-20, 표면 3.470%. 제공처는 주기를 12개월로 준다. */
    private static BondBasicInfo compoundBond() {
        return new BondBasicInfo(
                "KR104301DG40", "개인투자용국채 03470-2904(복리)", "대한민국",
                LocalDate.of(2026, 4, 20), LocalDate.of(2029, 4, 20),
                new BigDecimal("3.470"), 12, "복리채", "국채", null
        );
    }

    @Test
    @DisplayName("복리채는 중간에 이자를 지급하지 않는다 — 지급 주기가 만기와 같다")
    void compoundBondPaysOnlyAtMaturity() {
        JsonNode terms = termsJsonCodec.read(
                collect(compoundBond()).get(0).getSimulationTermsJson()
        );

        // 2026-08-04 → 2029-04-20 = 32개월(내림) → 32일 = 768시간
        assertEquals(768, terms.get("service_maturity_hours").asInt());
        assertEquals(
                768,
                terms.get("service_interest_interval_hours").asInt(),
                "복리채는 만기에 한 번만 지급하므로 주기가 만기와 같아야 합니다."
        );
    }

    @Test
    @DisplayName("복리채는 이자 주기를 응답에 담지 않는다 — 매년 받는 상품으로 오해하면 안 된다")
    void compoundBondOmitsInterestInterval() {
        JsonNode terms = termsJsonCodec.read(collect(compoundBond()).get(0).getRealTermsJson());

        assertNull(
                terms.get("interest_interval_months"),
                "제공처가 12개월을 주더라도 복리채는 지급 주기가 없습니다."
        );
        assertEquals("복리채", terms.get("interest_type").asText());
    }

    @Test
    @DisplayName("이표채는 제공처가 준 지급 주기를 그대로 쓴다")
    void couponBondKeepsProviderInterval() {
        JsonNode terms = termsJsonCodec.read(collect(corporateBond()).get(0).getRealTermsJson());

        assertEquals(3, terms.get("interest_interval_months").asInt());
        assertEquals("이표채", terms.get("interest_type").asText());
    }

    @Test
    @DisplayName("만기가 지난 채권은 제외한다")
    void skipsMaturedBond() {
        BondBasicInfo matured = new BondBasicInfo(
                "KR000000000", "만기지난채권", "발행사",
                LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1),
                new BigDecimal("2.0"), 3, "이표채", "일반회사채", "AAA"
        );

        assertTrue(collect(matured).isEmpty(), "만기가 지났으면 등록하지 않습니다.");
    }
}
