package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.dto.response.ProductPageResponse;
import org.firstfolio.simulation.dto.response.ProductSummaryResponse;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductQueryServiceTest {

    private FinancialProductMapper mapper;
    private FinancialProductQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        service = new FinancialProductQueryService(
                mapper,
                mock(org.firstfolio.simulation.mapper.ProductPriceMapper.class),
                new TermsJsonCodec()
        );
    }

    private static FinancialProduct product(long id, AssetType type, String displayName) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(id);
        product.setAssetType(type);
        product.setDisplayName(displayName);
        product.setRiskLevel("LOW");
        product.setSourceProvider("FSS_FINLIFE");
        product.setSourceProductCode("0010927:010300100335:6:단리:");
        product.setSourceProductName("국민은행 KB Star 정기예금 6개월 단리");
        product.setSourceReferenceAt(LocalDateTime.of(2026, 8, 4, 0, 0));
        product.setRealTermsJson("{\"interest_rate\":2.35,\"maturity_months\":6}");
        product.setSimulationTermsJson("{\"service_maturity_hours\":144}");
        product.setActive(true);

        return product;
    }

    private void given(FinancialProduct... products) {
        when(mapper.findPage(any(), any(), any(), anyInt())).thenReturn(List.of(products));
    }

    @Test
    @DisplayName("공개 상품만 조회한다 — 비공개 상품은 매퍼 단계에서 제외된다")
    void queriesOnlyActiveProducts() {
        given(product(1L, AssetType.DEPOSIT_SAVINGS, "푸른나무 정기예금"));

        service.findPage(null, null, 20);

        ArgumentCaptor<Boolean> activeCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(mapper).findPage(any(), activeCaptor.capture(), any(), anyInt());

        assertEquals(Boolean.TRUE, activeCaptor.getValue(), "공개 상품만 조회해야 합니다.");
    }

    @Test
    @DisplayName("응답에 원상품 식별 정보를 담지 않는다 — 사용자에게 노출 금지 (#5)")
    void neverExposesSourceIdentity() throws Exception {
        given(product(1L, AssetType.DEPOSIT_SAVINGS, "푸른나무 정기예금"));

        ProductSummaryResponse item = service.findPage(null, null, 20).getItems().get(0);

        // 응답 타입에 source_* 필드가 아예 없어야 한다. 게터가 있으면 컴파일은 되지만
        // 직렬화 결과에 남으므로, 프로퍼티 이름으로 확인한다.
        for (var descriptor : java.beans.Introspector.getBeanInfo(
                ProductSummaryResponse.class).getPropertyDescriptors()) {
            String name = descriptor.getName().toLowerCase();

            org.junit.jupiter.api.Assertions.assertFalse(
                    name.startsWith("source"),
                    "사용자 응답에 " + descriptor.getName() + " 가 있으면 안 됩니다."
            );
        }

        assertEquals("푸른나무 정기예금", item.getDisplayName());
    }

    @Test
    @DisplayName("예·적금과 채권은 압축 기간과 실제 기간을 병기한다 (#6)")
    void includesTermsForTimeCompressedProducts() {
        given(
                product(1L, AssetType.DEPOSIT_SAVINGS, "푸른나무 정기예금"),
                product(2L, AssetType.BOND, "새벽 국채")
        );

        for (ProductSummaryResponse item : service.findPage(null, null, 20).getItems()) {
            assertNotNull(item.getSimulationTerms(), item.getAssetType() + "은 압축 기간이 필요합니다.");
            assertNotNull(item.getRealTerms(), item.getAssetType() + "은 실제 기간이 필요합니다.");
        }
    }

    @Test
    @DisplayName("주식과 펀드는 두 기간 필드를 생략한다 — 만기가 없어 압축 대상이 아니다 (#6)")
    void omitsTermsForUncompressedProducts() {
        given(
                product(3L, AssetType.STOCK, "가온 전자"),
                product(4L, AssetType.FUND, "한아름 주식펀드")
        );

        for (ProductSummaryResponse item : service.findPage(null, null, 20).getItems()) {
            assertNull(item.getSimulationTerms(), item.getAssetType() + "은 압축 기간이 없어야 합니다.");
            assertNull(item.getRealTerms(), item.getAssetType() + "은 실제 기간이 없어야 합니다.");
        }
    }

    @Test
    @DisplayName("학습 완료 여부로 상품을 걸러내지 않는다")
    void doesNotFilterByLearningProgress() {
        given(
                product(1L, AssetType.DEPOSIT_SAVINGS, "푸른나무 정기예금"),
                product(2L, AssetType.BOND, "새벽 국채"),
                product(3L, AssetType.STOCK, "가온 전자"),
                product(4L, AssetType.FUND, "한아름 주식펀드")
        );

        // 사용자 정보를 받는 인자가 없다는 것이 곧 잠금이 없다는 뜻이다.
        assertEquals(4, service.findPage(null, null, 20).getItems().size());
    }

    @Test
    @DisplayName("자산군으로 거를 수 있다")
    void filtersByAssetType() {
        given(product(3L, AssetType.STOCK, "가온 전자"));

        service.findPage("stock", null, 20);

        ArgumentCaptor<AssetType> captor = ArgumentCaptor.forClass(AssetType.class);
        verify(mapper).findPage(captor.capture(), any(), any(), anyInt());

        assertEquals(AssetType.STOCK, captor.getValue());
    }

    @Test
    @DisplayName("잘못된 자산군 필터는 INVALID_PRODUCT_FILTER")
    void rejectsInvalidAssetType() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findPage("REAL_ESTATE", null, 20)
        );

        assertEquals(ErrorCode.INVALID_PRODUCT_FILTER, exception.getErrorCode());
    }

    @Test
    @DisplayName("숫자가 아닌 커서는 INVALID_PRODUCT_FILTER")
    void rejectsInvalidCursor() {
        assertThrows(ApiException.class, () -> service.findPage(null, "abc", 20));
    }

    @Test
    @DisplayName("다음 페이지가 있으면 커서를 주고, 없으면 null이다")
    void returnsCursorOnlyWhenMorePagesExist() {
        when(mapper.findPage(any(), any(), any(), eq(3)))
                .thenReturn(List.of(
                        product(1L, AssetType.DEPOSIT_SAVINGS, "A"),
                        product(2L, AssetType.DEPOSIT_SAVINGS, "B"),
                        product(3L, AssetType.DEPOSIT_SAVINGS, "C")
                ));

        ProductPageResponse page = service.findPage(null, null, 2);

        assertEquals(2, page.getItems().size());
        assertEquals("2", page.getNextCursor());

        when(mapper.findPage(any(), any(), any(), eq(3)))
                .thenReturn(List.of(product(1L, AssetType.DEPOSIT_SAVINGS, "A")));

        assertNull(service.findPage(null, null, 2).getNextCursor());
    }

    @Test
    @DisplayName("페이지 크기는 상한을 넘지 않는다")
    void capsPageSize() {
        given(product(1L, AssetType.DEPOSIT_SAVINGS, "A"));

        service.findPage(null, null, 9999);

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mapper).findPage(any(), any(), any(), captor.capture());

        assertEquals(101, captor.getValue(), "상한 100 + 다음 페이지 확인용 1건");
    }
}
