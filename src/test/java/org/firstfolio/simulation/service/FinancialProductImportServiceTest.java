package org.firstfolio.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.simulation.client.finlife.FinlifeClient;
import org.firstfolio.simulation.client.finlife.FinlifeProduct;
import org.firstfolio.simulation.client.finlife.FinlifeProductType;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductImportServiceTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 4, 0, 0);

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    private FinlifeClient finlifeClient;
    private FinancialProductWriter writer;
    private FinancialProductImportService service;

    @BeforeEach
    void setUp() {
        finlifeClient = mock(FinlifeClient.class);
        writer = mock(FinancialProductWriter.class);
        service = new FinancialProductImportService(
                finlifeClient,
                new TimeCompressionPolicy(24),
                writer
        );

        when(finlifeClient.fetchAll(FinlifeProductType.SAVING)).thenReturn(List.of());
    }

    private static FinlifeProduct deposit(int months, String rate) {
        return new FinlifeProduct(
                FinlifeProductType.DEPOSIT,
                "0010927",
                "010300100335",
                months,
                "국민은행",
                "KB Star 정기예금",
                new BigDecimal(rate),
                "단리",
                null,
                "202607"
        );
    }

    private List<FinancialProduct> capturePersisted() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinancialProduct>> captor = ArgumentCaptor.forClass(List.class);

        verify(writer).registerNew(captor.capture(), eq(REFERENCE_AT), any());

        return captor.getValue();
    }

    @Test
    @DisplayName("수집한 상품을 비공개 상태로 등록한다 — 관리자 검토 전에는 공개하지 않는다")
    void registersProductsAsInactive() {
        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT))
                .thenReturn(List.of(deposit(6, "2.35")));

        service.importProducts("FSS_FINLIFE", REFERENCE_AT);

        FinancialProduct product = capturePersisted().get(0);

        assertFalse(product.isActive(), "검토 전 상품은 비공개여야 합니다.");
        assertEquals(AssetType.DEPOSIT_SAVINGS, product.getAssetType());
        assertEquals("LOW", product.getRiskLevel());
    }

    @Test
    @DisplayName("원상품 식별 코드에 회사·상품·만기를 모두 담는다")
    void buildsCompositeSourceCode() {
        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT))
                .thenReturn(List.of(deposit(6, "2.35")));

        service.importProducts("FSS_FINLIFE", REFERENCE_AT);

        FinancialProduct product = capturePersisted().get(0);

        assertEquals("0010927:010300100335:6", product.getSourceProductCode());
        assertEquals("국민은행 KB Star 정기예금 6개월", product.getSourceProductName());
    }

    @Test
    @DisplayName("실제 만기에 압축 배율을 적용해 서비스 내 기간을 계산한다")
    void appliesTimeCompression() throws Exception {
        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT))
                .thenReturn(List.of(deposit(6, "2.35")));

        service.importProducts("FSS_FINLIFE", REFERENCE_AT);

        JsonNode terms = objectMapper.readTree(
                capturePersisted().get(0).getSimulationTermsJson()
        );

        assertEquals(144, terms.get("service_maturity_hours").asInt());
        assertEquals(24, terms.get("compression_hours_per_month").asInt());
    }

    @Test
    @DisplayName("가져온 금리를 그대로 쓰고, 출처가 없는 이자 주기는 가정치로 표시한다")
    void keepsSourceRateAndFlagsAssumedInterval() throws Exception {
        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT))
                .thenReturn(List.of(deposit(6, "2.35")));

        service.importProducts("FSS_FINLIFE", REFERENCE_AT);

        JsonNode terms = objectMapper.readTree(
                capturePersisted().get(0).getRealTermsJson()
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
        when(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT))
                .thenReturn(List.of(deposit(0, "2.35"), deposit(6, "2.35")));

        service.importProducts("FSS_FINLIFE", REFERENCE_AT);

        assertEquals(1, capturePersisted().size());
    }

    @Test
    @DisplayName("지원하지 않는 제공처는 거부한다")
    void rejectsUnknownProvider() {
        assertThrows(
                ApiException.class,
                () -> service.importProducts("UNKNOWN_SOURCE", REFERENCE_AT)
        );
    }
}
