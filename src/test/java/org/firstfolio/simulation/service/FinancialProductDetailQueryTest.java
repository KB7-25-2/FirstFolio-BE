package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.dto.response.ProductDetailResponse;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductDetailQueryTest {

    private static final long PRODUCT_ID = 25L;

    private FinancialProductMapper productMapper;
    private ProductPriceMapper priceMapper;
    private FinancialProductQueryService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(FinancialProductMapper.class);
        priceMapper = mock(ProductPriceMapper.class);
        service = new FinancialProductQueryService(productMapper, priceMapper, new TermsJsonCodec());
    }

    private static FinancialProduct product(AssetType type, String provider) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(PRODUCT_ID);
        product.setAssetType(type);
        product.setDisplayName("푸른나무 정기예금");
        product.setDescription("반년 만기 예금입니다.");
        product.setRiskLevel("LOW");
        product.setSourceProvider(provider);
        product.setSourceProductCode("0010927:010300100335:6:단리:");
        product.setSourceProductName("국민은행 KB Star 정기예금 6개월 단리");
        product.setSourceReferenceAt(LocalDateTime.of(2026, 8, 4, 0, 0));
        product.setRealTermsJson("{\"interest_rate\":2.35,\"maturity_months\":6}");
        product.setSimulationTermsJson("{\"service_maturity_hours\":144}");
        product.setActive(true);

        return product;
    }

    private void givenProduct(FinancialProduct product) {
        when(productMapper.findActiveById(PRODUCT_ID)).thenReturn(product);
    }

    @Test
    @DisplayName("공개 상품만 조회한다 — 비공개는 존재해도 찾을 수 없다")
    void looksUpOnlyActiveProducts() {
        givenProduct(product(AssetType.DEPOSIT_SAVINGS, "FSS_FINLIFE"));

        service.findById(PRODUCT_ID);

        // findById가 아니라 findActiveById를 써야 비공개 상품이 새지 않는다.
        verify(productMapper).findActiveById(PRODUCT_ID);
    }

    @Test
    @DisplayName("없거나 비공개인 상품은 PRODUCT_NOT_FOUND")
    void rejectsMissingOrInactiveProduct() {
        when(productMapper.findActiveById(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findById(PRODUCT_ID)
        );

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @DisplayName("내부 제공처 식별자 대신 공개 가능한 표시명을 준다")
    @CsvSource({
            "FSS_FINLIFE,     공개 예·적금 상품 정보",
            "DATA_GO_KR_BOND, 공개 채권 정보",
            "DATA_GO_KR_ETF,  공개 상장지수상품 시세",
            "TOSSINVEST,      공개 주식 시세"
    })
    void masksInternalSourceProvider(String provider, String expectedLabel) {
        givenProduct(product(AssetType.DEPOSIT_SAVINGS, provider));

        ProductDetailResponse response = service.findById(PRODUCT_ID);

        assertEquals(expectedLabel, response.getSource().getProvider());
        assertFalse(
                response.getSource().getProvider().contains(provider),
                "내부 식별자가 그대로 노출되면 안 됩니다."
        );
    }

    @Test
    @DisplayName("출처 기준 시점을 함께 준다")
    void includesSourceReferenceAt() {
        givenProduct(product(AssetType.DEPOSIT_SAVINGS, "FSS_FINLIFE"));

        assertEquals(
                LocalDateTime.of(2026, 8, 4, 0, 0),
                service.findById(PRODUCT_ID).getSource().getReferenceAt()
        );
    }

    @Test
    @DisplayName("저장된 기준 가격이 있으면 가격과 기준 시각을 준다")
    void includesPriceWhenAvailable() {
        givenProduct(product(AssetType.STOCK, "TOSSINVEST"));

        ProductPrice price = new ProductPrice();
        price.setPrice(new BigDecimal("241500.0000"));
        price.setReferenceAt(LocalDateTime.of(2026, 8, 4, 8, 43));
        when(priceMapper.findLatestByProductId(PRODUCT_ID)).thenReturn(price);

        ProductDetailResponse response = service.findById(PRODUCT_ID);

        assertEquals(new BigDecimal("241500.0000"), response.getCurrentPrice());
        assertEquals(LocalDateTime.of(2026, 8, 4, 8, 43), response.getPriceReferenceAt());
    }

    @Test
    @DisplayName("기준 가격이 없으면 임의 값을 만들지 않고 두 필드를 생략한다")
    void omitsPriceWhenMissing() {
        givenProduct(product(AssetType.STOCK, "TOSSINVEST"));
        when(priceMapper.findLatestByProductId(PRODUCT_ID)).thenReturn(null);

        ProductDetailResponse response = service.findById(PRODUCT_ID);

        assertNull(response.getCurrentPrice(), "없는 가격을 지어내면 안 됩니다.");
        assertNull(response.getPriceReferenceAt());
    }

    @Test
    @DisplayName("예·적금은 압축 기간과 실제 기간을 병기한다")
    void includesTermsForCompressedProduct() {
        givenProduct(product(AssetType.DEPOSIT_SAVINGS, "FSS_FINLIFE"));

        ProductDetailResponse response = service.findById(PRODUCT_ID);

        assertNotNull(response.getSimulationTerms());
        assertNotNull(response.getRealTerms());
        assertEquals(144, response.getSimulationTerms().get("service_maturity_hours").asInt());
    }

    @Test
    @DisplayName("주식은 두 기간 필드를 생략한다")
    void omitsTermsForStock() {
        givenProduct(product(AssetType.STOCK, "TOSSINVEST"));

        ProductDetailResponse response = service.findById(PRODUCT_ID);

        assertNull(response.getSimulationTerms());
        assertNull(response.getRealTerms());
    }

    @Test
    @DisplayName("펀드도 두 기간 필드를 생략한다")
    void omitsTermsForFund() {
        givenProduct(product(AssetType.FUND, "DATA_GO_KR_ETF"));

        ProductDetailResponse response = service.findById(PRODUCT_ID);

        assertNull(response.getSimulationTerms());
        assertNull(response.getRealTerms());
    }

    @Test
    @DisplayName("상세 응답에도 원상품 식별 필드를 두지 않는다")
    void neverExposesSourceIdentity() throws Exception {
        for (var descriptor : java.beans.Introspector.getBeanInfo(
                ProductDetailResponse.class).getPropertyDescriptors()) {
            String name = descriptor.getName().toLowerCase();

            assertFalse(
                    name.startsWith("sourceproduct"),
                    "상세 응답에 " + descriptor.getName() + " 가 있으면 안 됩니다."
            );
        }
    }
}
