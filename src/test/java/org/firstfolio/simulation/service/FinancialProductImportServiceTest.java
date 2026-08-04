package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.service.collector.ProductCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이 서비스는 수집기를 고르고 저장을 위임하는 역할만 한다.
 * 자산군별 변환 규칙은 각 {@link ProductCollector} 테스트에서 검증한다.
 */
class FinancialProductImportServiceTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 4, 0, 0);

    /** 어떤 제공처를 담당하는지, 호출됐는지만 기록하는 수집기. */
    private static final class RecordingCollector implements ProductCollector {

        private final String provider;
        private final List<FinancialProduct> result;
        private boolean called;

        RecordingCollector(String provider, int productCount) {
            this.provider = provider;
            this.result = new ArrayList<>();

            for (int i = 0; i < productCount; i++) {
                this.result.add(new FinancialProduct());
            }
        }

        @Override
        public String sourceProvider() {
            return provider;
        }

        @Override
        public List<FinancialProduct> collect(LocalDateTime referenceAt, LocalDateTime now) {
            called = true;
            return result;
        }
    }

    @Test
    @DisplayName("source_provider에 맞는 수집기만 호출한다")
    void dispatchesToMatchingCollector() {
        RecordingCollector deposit = new RecordingCollector("FSS_FINLIFE", 2);
        RecordingCollector bond = new RecordingCollector("DATA_GO_KR_BOND", 5);
        FinancialProductWriter writer = mock(FinancialProductWriter.class);

        when(writer.registerNew(anyList(), any(), any()))
                .thenReturn(new ProductImportResult(0, List.of(1L, 2L, 3L, 4L, 5L), REFERENCE_AT));

        new FinancialProductImportService(List.of(deposit, bond), writer)
                .importProducts("DATA_GO_KR_BOND", REFERENCE_AT);

        assertTrue(bond.called, "채권 수집기가 호출되어야 합니다.");
        assertEquals(false, deposit.called, "다른 제공처의 수집기는 호출하지 않습니다.");
    }

    @Test
    @DisplayName("수집 결과를 그대로 저장에 넘긴다")
    void passesCollectedProductsToWriter() {
        FinancialProductWriter writer = mock(FinancialProductWriter.class);

        when(writer.registerNew(anyList(), any(), any()))
                .thenReturn(new ProductImportResult(0, List.of(1L, 2L), REFERENCE_AT));

        ProductImportResult result =
                new FinancialProductImportService(
                        List.of(new RecordingCollector("FSS_FINLIFE", 2)), writer
                ).importProducts("FSS_FINLIFE", REFERENCE_AT);

        assertEquals(2, result.getImportedCount());
    }

    @Test
    @DisplayName("지원하지 않는 제공처는 거부하고 저장하지 않는다")
    void rejectsUnknownProvider() {
        FinancialProductWriter writer = mock(FinancialProductWriter.class);

        FinancialProductImportService service = new FinancialProductImportService(
                List.of(new RecordingCollector("FSS_FINLIFE", 1)), writer
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.importProducts("UNKNOWN_SOURCE", REFERENCE_AT)
        );

        assertEquals(ErrorCode.INVALID_SOURCE_PRODUCT, exception.getErrorCode());
        assertTrue(
                exception.getMessage().contains("FSS_FINLIFE"),
                "쓸 수 있는 제공처를 오류 메시지에 알려줘야 합니다."
        );
        verify(writer, never()).registerNew(anyList(), any(), any());
    }
}
