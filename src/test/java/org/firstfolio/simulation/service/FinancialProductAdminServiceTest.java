package org.firstfolio.simulation.service;

import org.firstfolio.admin.dto.request.ProductUpdateRequest;
import org.firstfolio.admin.dto.response.AdminProductPageResponse;
import org.firstfolio.admin.dto.response.AdminProductResponse;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductAdminServiceTest {

    private static final long PRODUCT_ID = 25L;
    private static final String REAL_NAME = "국민은행 KB Star 정기예금 6개월";

    private FinancialProductMapper mapper;
    private FinancialProductAdminService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        service = new FinancialProductAdminService(mapper);
    }

    private static FinancialProduct product(String displayName, boolean active) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(PRODUCT_ID);
        product.setAssetType(AssetType.DEPOSIT_SAVINGS);
        product.setDisplayName(displayName);
        product.setSourceProvider("FSS_FINLIFE");
        product.setSourceProductCode("0010927:010300100335:6");
        product.setSourceProductName(REAL_NAME);
        product.setSourceReferenceAt(LocalDateTime.of(2026, 8, 4, 0, 0));
        product.setRealTermsJson("{\"interest_rate\":2.35,\"maturity_months\":6}");
        product.setSimulationTermsJson("{\"service_maturity_hours\":144}");
        product.setRiskLevel("LOW");
        product.setActive(active);

        return product;
    }

    @Test
    @DisplayName("가명을 붙이지 않은 상품은 공개할 수 없다 — 실제 상품명 그대로면 거부")
    void rejectsActivationWhenDisplayNameIsStillTheRealName() {
        when(mapper.findById(PRODUCT_ID)).thenReturn(product(REAL_NAME, false));

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setStatus("ACTIVE");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.update(PRODUCT_ID, request)
        );

        assertEquals(ErrorCode.INVALID_SOURCE_PRODUCT, exception.getErrorCode());
        verify(mapper, never()).updateEditableFields(any());
    }

    @Test
    @DisplayName("대소문자만 다른 실제 상품명도 거부한다")
    void rejectsActivationIgnoringCase() {
        when(mapper.findById(PRODUCT_ID)).thenReturn(product(REAL_NAME, false));

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setDisplayName(REAL_NAME.toUpperCase());
        request.setStatus("ACTIVE");

        assertThrows(ApiException.class, () -> service.update(PRODUCT_ID, request));
        verify(mapper, never()).updateEditableFields(any());
    }

    @Test
    @DisplayName("가명을 입력하면 공개 전환된다")
    void activatesWhenPseudonymGiven() {
        when(mapper.findById(PRODUCT_ID))
                .thenReturn(product(REAL_NAME, false))
                .thenReturn(product("푸른나무 정기예금", true));

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setDisplayName("푸른나무 정기예금");
        request.setStatus("ACTIVE");

        AdminProductResponse response = service.update(PRODUCT_ID, request);

        ArgumentCaptor<FinancialProduct> captor =
                ArgumentCaptor.forClass(FinancialProduct.class);
        verify(mapper).updateEditableFields(captor.capture());

        assertTrue(captor.getValue().isActive());
        assertEquals("푸른나무 정기예금", captor.getValue().getDisplayName());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    @DisplayName("비공개로 되돌릴 때는 가명 검사를 하지 않는다")
    void allowsDeactivationWithoutPseudonym() {
        when(mapper.findById(PRODUCT_ID))
                .thenReturn(product(REAL_NAME, true))
                .thenReturn(product(REAL_NAME, false));

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setStatus("INACTIVE");

        AdminProductResponse response = service.update(PRODUCT_ID, request);

        assertEquals("INACTIVE", response.getStatus());
    }

    @Test
    @DisplayName("없는 상품을 수정하면 PRODUCT_NOT_FOUND")
    void rejectsUnknownProduct() {
        when(mapper.findById(PRODUCT_ID)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.update(PRODUCT_ID, new ProductUpdateRequest())
        );

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("관리자 목록은 실제 상품명과 실제 조건을 함께 보여준다 — 가명을 지으려면 필요하다")
    void adminListExposesSourceInfo() {
        when(mapper.findPage(any(), any(), any(), anyInt()))
                .thenReturn(List.of(product(REAL_NAME, false)));

        AdminProductResponse item = service.findPage(null, null, null, 20).getItems().get(0);

        assertEquals(REAL_NAME, item.getSourceProductName());
        assertEquals("0010927:010300100335:6", item.getSourceProductCode());
        assertEquals("2.35", item.getRealTerms().get("interest_rate").asText());
        assertEquals(144, item.getSimulationTerms().get("service_maturity_hours").asInt());
        assertEquals("INACTIVE", item.getStatus());
    }

    @Test
    @DisplayName("다음 페이지가 있으면 커서를 주고, 없으면 null이다")
    void returnsCursorOnlyWhenMorePagesExist() {
        FinancialProduct first = product("A", false);
        FinancialProduct second = product("B", false);
        second.setProductId(26L);

        when(mapper.findPage(any(), any(), any(), eq(3)))
                .thenReturn(List.of(first, second, product("C", false)));

        AdminProductPageResponse page = service.findPage(null, null, null, 2);

        assertEquals(2, page.getItems().size());
        assertEquals("26", page.getNextCursor());

        when(mapper.findPage(any(), any(), any(), eq(3)))
                .thenReturn(List.of(first));

        assertNull(service.findPage(null, null, null, 2).getNextCursor());
    }

    @Test
    @DisplayName("상태 필터는 ACTIVE/INACTIVE만 받는다")
    void rejectsInvalidStatusFilter() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findPage(null, "DRAFT", null, 20)
        );

        assertEquals(ErrorCode.INVALID_PRODUCT_FILTER, exception.getErrorCode());
    }

    @Test
    @DisplayName("잘못된 자산군 필터는 INVALID_PRODUCT_FILTER")
    void rejectsInvalidAssetTypeFilter() {
        assertThrows(
                ApiException.class,
                () -> service.findPage("REAL_ESTATE", null, null, 20)
        );
    }

    @Test
    @DisplayName("status를 보내지 않으면 공개 여부를 바꾸지 않는다")
    void keepsCurrentStatusWhenNotGiven() {
        when(mapper.findById(PRODUCT_ID))
                .thenReturn(product("푸른나무 정기예금", true))
                .thenReturn(product("새 이름", true));

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setDisplayName("새 이름");

        service.update(PRODUCT_ID, request);

        ArgumentCaptor<FinancialProduct> captor =
                ArgumentCaptor.forClass(FinancialProduct.class);
        verify(mapper).updateEditableFields(captor.capture());

        assertTrue(captor.getValue().isActive(), "기존 공개 상태가 유지되어야 합니다.");
    }

    @Test
    @DisplayName("빈 가명으로는 공개할 수 없다")
    void rejectsBlankPseudonymOnActivation() {
        when(mapper.findById(PRODUCT_ID)).thenReturn(product(REAL_NAME, false));

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setDisplayName("   ");
        request.setStatus("ACTIVE");

        assertThrows(ApiException.class, () -> service.update(PRODUCT_ID, request));
        verify(mapper, never()).updateEditableFields(any());
    }

    @Test
    @DisplayName("등록 직후 상품은 비공개다")
    void newlyImportedProductIsInactive() {
        assertFalse(product(REAL_NAME, false).isActive());
    }
}
