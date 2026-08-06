package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceRefreshServiceTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 8, 6, 4, 20, 0);

    private FinancialProductMapper financialProductMapper;
    private ProductPriceMapper productPriceMapper;
    private TossInvestClient tossInvestClient;
    private PriceRefreshService service;

    private final List<FinancialProduct> targets = new ArrayList<>();
    private final List<TossPricesResponse.Item> quotes = new ArrayList<>();
    private final List<ProductPrice> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        financialProductMapper = mock(FinancialProductMapper.class);
        productPriceMapper = mock(ProductPriceMapper.class);
        tossInvestClient = mock(TossInvestClient.class);

        service = new PriceRefreshService(
                financialProductMapper,
                productPriceMapper,
                tossInvestClient,
                new BigDecimal("0.30")
        );

        targets.clear();
        quotes.clear();
        saved.clear();

        when(financialProductMapper.findPriceTargets(anyList(), any())).thenReturn(targets);
        when(productPriceMapper.findLatestByProductIds(anyList())).thenReturn(List.of());
        when(tossInvestClient.fetchPrices(anyList())).thenReturn(quotes);

        org.mockito.Mockito.doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return null;
        }).when(productPriceMapper).insert(any(ProductPrice.class));
    }

    private FinancialProduct product(long productId, String code, AssetType assetType) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(productId);
        product.setAssetType(assetType);
        product.setSourceProductCode(code);
        product.setActive(true);

        targets.add(product);

        return product;
    }

    private void quote(String symbol, String lastPrice, String timestamp) {
        TossPricesResponse.Item item = new TossPricesResponse.Item();

        item.setSymbol(symbol);
        item.setLastPrice(lastPrice == null ? null : new BigDecimal(lastPrice));
        item.setTimestamp(timestamp);

        quotes.add(item);
    }

    private PriceRefreshResult refresh() {
        return service.refresh(REFERENCE_AT, null);
    }

    @Test
    @DisplayName("주식과 펀드를 한 번의 호출로 갱신한다")
    void refreshesStocksAndFundsInOneCall() {
        product(87L, "005930", AssetType.STOCK);
        product(97L, "069500", AssetType.FUND);
        quote("005930", "230250", "2026-08-06T13:19:15.000+09:00");
        quote("069500", "98640", "2026-08-06T13:19:15.000+09:00");

        PriceRefreshResult result = refresh();

        assertEquals(2, result.getProcessedCount());
        assertEquals(2, result.getCreatedCount());
        assertEquals(0, result.getSkippedCount());
        verify(tossInvestClient).fetchPrices(List.of("005930", "069500"));
    }

    @Test
    @DisplayName("기준 시각은 체결 시각이 아니라 요청받은 갱신 시점으로 저장한다")
    void storesRequestedReferenceAtNotQuoteTime() {
        product(87L, "005930", AssetType.STOCK);
        // 체결은 8분 전이다 (유동성이 낮은 종목에서 실제로 나온 상황).
        quote("005930", "230250", "2026-08-06T13:11:00.000+09:00");

        refresh();

        assertEquals(REFERENCE_AT, saved.get(0).getReferenceAt());
    }

    @Test
    @DisplayName("생성 키에 제공처와 실제 체결 시각을 남긴다")
    void keepsSourceAndQuoteTimeInGenerationKey() {
        product(87L, "005930", AssetType.STOCK);
        quote("005930", "230250", "2026-08-06T13:19:15.000+09:00");

        refresh();

        // KST 13:19:15 → UTC 04:19:15
        assertEquals("toss:005930:2026-08-06T04:19:15Z", saved.get(0).getGenerationKey());
    }

    @Test
    @DisplayName("체결 시각을 읽을 수 없으면 기준 시각으로 대신한다 — 가격을 버리지 않는다")
    void fallsBackToReferenceAtWhenQuoteTimeIsUnreadable() {
        product(87L, "005930", AssetType.STOCK);
        quote("005930", "230250", "이상한 값");

        refresh();

        assertEquals("toss:005930:2026-08-06T04:20:00Z", saved.get(0).getGenerationKey());
        assertEquals(1, saved.size(), "가격 자체는 저장돼야 합니다.");
    }

    @Test
    @DisplayName("응답에서 빠진 종목은 건너뛴다 — 토스는 모르는 코드를 조용히 뺀다")
    void skipsSymbolsMissingFromResponse() {
        product(87L, "005930", AssetType.STOCK);
        product(99L, "999999", AssetType.STOCK);
        quote("005930", "230250", "2026-08-06T13:19:15.000+09:00");

        PriceRefreshResult result = refresh();

        assertEquals(2, result.getProcessedCount());
        assertEquals(1, result.getCreatedCount());
        assertEquals(1, result.getSkippedCount());
    }

    @Test
    @DisplayName("가격이 없거나 0 이하면 저장하지 않는다 — 임의 값을 만들지 않는다")
    void rejectsInvalidPrices() {
        product(87L, "005930", AssetType.STOCK);
        product(88L, "000660", AssetType.STOCK);
        quote("005930", null, "2026-08-06T13:19:15.000+09:00");
        quote("000660", "0", "2026-08-06T13:19:15.000+09:00");

        PriceRefreshResult result = refresh();

        assertEquals(0, result.getCreatedCount());
        assertEquals(2, result.getSkippedCount());
        verify(productPriceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("이미 저장된 가격이면 건너뛴다 — 배치를 다시 돌려도 중복되지 않는다")
    void skipsWhenAlreadyStored() {
        product(87L, "005930", AssetType.STOCK);
        quote("005930", "230250", "2026-08-06T13:19:15.000+09:00");

        doThrow(new DuplicateKeyException("uq_product_prices_product_time"))
                .when(productPriceMapper).insert(any(ProductPrice.class));

        PriceRefreshResult result = refresh();

        assertEquals(1, result.getProcessedCount());
        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getSkippedCount());
    }

    @Test
    @DisplayName("변동이 커도 저장한다 — 실제 시세를 거부하면 평가·거래가 막힌다")
    void storesEvenWhenPriceJumps() {
        product(87L, "005930", AssetType.STOCK);
        quote("005930", "500000", "2026-08-06T13:19:15.000+09:00");

        ProductPrice previous = new ProductPrice();
        previous.setProductId(87L);
        previous.setPrice(new BigDecimal("230250"));

        when(productPriceMapper.findLatestByProductIds(anyList())).thenReturn(List.of(previous));

        assertEquals(1, refresh().getCreatedCount());
    }

    @Test
    @DisplayName("가격은 REAL_DATA로, 자릿수는 소수 넷째 자리로 저장한다")
    void storesRealDataWithPriceScale() {
        product(87L, "005930", AssetType.STOCK);
        quote("005930", "230250", "2026-08-06T13:19:15.000+09:00");

        refresh();

        ArgumentCaptor<ProductPrice> captor = ArgumentCaptor.forClass(ProductPrice.class);
        verify(productPriceMapper).insert(captor.capture());

        assertEquals("REAL_DATA", captor.getValue().getSourceType());
        assertEquals(new BigDecimal("230250.0000"), captor.getValue().getPrice());
    }

    @Test
    @DisplayName("종목코드가 없는 상품은 조회 대상에서 빠진다")
    void skipsProductsWithoutSymbol() {
        product(87L, "005930", AssetType.STOCK);
        product(88L, null, AssetType.STOCK);
        quote("005930", "230250", "2026-08-06T13:19:15.000+09:00");

        PriceRefreshResult result = refresh();

        verify(tossInvestClient).fetchPrices(List.of("005930"));
        assertEquals(2, result.getProcessedCount(), "대상 수는 그대로 셉니다.");
        assertEquals(1, result.getSkippedCount());
    }

    @Test
    @DisplayName("대상이 없으면 외부를 부르지 않는다")
    void doesNotCallExternalWhenNoTargets() {
        PriceRefreshResult result = refresh();

        assertEquals(0, result.getProcessedCount());
        verify(tossInvestClient, never()).fetchPrices(anyList());
    }

    @Test
    @DisplayName("미래 시점의 가격은 만들지 않는다")
    void rejectsFutureReferenceAt() {
        LocalDateTime future = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

        ApiException exception =
                assertThrows(ApiException.class, () -> service.refresh(future, null));

        assertEquals(ErrorCode.PRICE_POLICY_INVALID, exception.getErrorCode());
        verify(financialProductMapper, never()).findPriceTargets(anyList(), any());
    }

    @Test
    @DisplayName("기준 시점이 없으면 거부한다")
    void rejectsMissingReferenceAt() {
        ApiException exception =
                assertThrows(ApiException.class, () -> service.refresh(null, null));

        assertEquals(ErrorCode.PRICE_POLICY_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("가격으로 평가하는 자산군만 대상으로 조회한다")
    void looksUpOnlyPriceBasedAssetTypes() {
        refresh();

        ArgumentCaptor<List<AssetType>> captor = ArgumentCaptor.forClass(List.class);
        verify(financialProductMapper).findPriceTargets(captor.capture(), any());

        assertTrue(captor.getValue().contains(AssetType.STOCK));
        assertTrue(captor.getValue().contains(AssetType.FUND));
        assertEquals(2, captor.getValue().size(), "예·적금·채권은 원금으로 평가합니다.");
    }
}
