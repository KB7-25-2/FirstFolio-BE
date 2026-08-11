package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.AssetAllocation;
import org.firstfolio.portfolio.domain.HoldingStatus;
import org.firstfolio.portfolio.domain.HoldingValuation;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioValuation;
import org.firstfolio.portfolio.domain.ValuationBasis;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.firstfolio.simulation.service.CurrentPriceReader;
import org.firstfolio.simulation.service.PriceCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioValuationServiceTest {

    private static final long PORTFOLIO_ID = 8001L;
    private static final LocalDateTime PRICE_TIME = LocalDateTime.of(2026, 7, 29, 3, 0);

    private PortfolioHoldingMapper holdingMapper;
    private ProductPriceMapper productPriceMapper;
    private PriceCache priceCache;
    private PortfolioValuationService service;

    private final List<PortfolioHolding> holdings = new ArrayList<>();
    private final List<ProductPrice> prices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        holdingMapper = mock(PortfolioHoldingMapper.class);
        productPriceMapper = mock(ProductPriceMapper.class);
        priceCache = new PriceCache();

        // CurrentPriceReader를 모킹하지 않고 진짜를 쓴다 — 캐시 미스 시 DB로 넘어가는 경로까지
        // 함께 지나야 "평가와 체결이 같은 값을 본다"는 것이 실제로 확인된다.
        service = new PortfolioValuationService(
                holdingMapper,
                new CurrentPriceReader(priceCache, productPriceMapper)
        );

        holdings.clear();
        prices.clear();

        when(holdingMapper.findActiveByPortfolioId(anyLong())).thenReturn(holdings);
        when(productPriceMapper.findLatestByProductIds(any())).thenReturn(prices);
    }

    private Portfolio portfolio(String cashBalance) {
        Portfolio portfolio = new Portfolio();

        portfolio.setPortfolioId(PORTFOLIO_ID);
        portfolio.setGenerationNo(1);
        portfolio.setInitialAmount(new BigDecimal("30000000.00"));
        portfolio.setCashBalance(new BigDecimal(cashBalance));

        return portfolio;
    }

    private PortfolioHolding holding(
            long holdingId,
            long productId,
            AssetType assetType,
            String quantity,
            String principal
    ) {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setHoldingId(holdingId);
        holding.setPortfolioId(PORTFOLIO_ID);
        holding.setProductId(productId);
        holding.setQuantity(new BigDecimal(quantity));
        holding.setPrincipalAmount(new BigDecimal(principal));
        holding.setStatus(HoldingStatus.ACTIVE);
        holding.setProductDisplayName("가명 상품 " + productId);
        holding.setProductAssetType(assetType);

        holdings.add(holding);

        return holding;
    }

    private void price(long productId, String price) {
        price(productId, price, PRICE_TIME);
    }

    private void price(long productId, String price, LocalDateTime referenceAt) {
        ProductPrice productPrice = new ProductPrice();

        productPrice.setProductId(productId);
        productPrice.setPrice(new BigDecimal(price));
        productPrice.setReferenceAt(referenceAt);

        prices.add(productPrice);
    }

    @Test
    @DisplayName("주식·펀드는 보유 수량 × 마지막 기준 가격으로 평가한다")
    void valuesPriceBasedHoldingsWithLatestPrice() {
        holding(8101L, 25L, AssetType.STOCK, "10.000000", "700000.00");
        price(25L, "75000.0000");

        HoldingValuation valuation = service.valuate(portfolio("0.00")).getHoldings().get(0);

        assertEquals(new BigDecimal("750000.00"), valuation.getValuationAmount());
        assertEquals(ValuationBasis.MARKET_PRICE, valuation.getBasis());
        assertEquals(PRICE_TIME, valuation.getValuedAt(), "가격 기준 시점을 그대로 밝혀야 합니다.");
    }

    @Test
    @DisplayName("예·적금·채권은 원금으로 평가한다 — 이자는 현금으로 들어오므로 여기서 더하지 않는다")
    void valuesSubscriptionHoldingsWithPrincipal() {
        holding(8101L, 30L, AssetType.DEPOSIT_SAVINGS, "1.000000", "10000000.00");

        HoldingValuation valuation = service.valuate(portfolio("0.00")).getHoldings().get(0);

        assertEquals(new BigDecimal("10000000.00"), valuation.getValuationAmount());
        assertEquals(ValuationBasis.PRINCIPAL, valuation.getBasis());
    }

    @Test
    @DisplayName("가입형만 있으면 가격을 조회하지 않는다")
    void doesNotLookUpPricesForSubscriptionOnlyPortfolio() {
        holding(8101L, 30L, AssetType.DEPOSIT_SAVINGS, "1.000000", "10000000.00");
        holding(8102L, 31L, AssetType.BOND, "1.000000", "5000000.00");

        service.valuate(portfolio("0.00"));

        verify(productPriceMapper, never()).findLatestByProductIds(any());
    }

    @Test
    @DisplayName("기준 가격이 없으면 임의 값을 만들지 않고 원금과 함께 오류 상태로 표시한다")
    void marksHoldingWhenPriceIsMissing() {
        holding(8101L, 25L, AssetType.STOCK, "10.000000", "700000.00");
        // 가격을 넣지 않는다 (수집 실패 또는 최초 수집 전).

        PortfolioValuation valuation = service.valuate(portfolio("0.00"));
        HoldingValuation holding = valuation.getHoldings().get(0);

        assertEquals(ValuationBasis.PRICE_UNAVAILABLE, holding.getBasis());
        assertEquals(new BigDecimal("700000.00"), holding.getValuationAmount(), "매입 원금으로 대신합니다.");
        assertNull(holding.getValuedAt(), "평가 기준 시점을 지어내면 안 됩니다.");
        assertTrue(valuation.hasStalePrice());
    }

    @Test
    @DisplayName("가격이 모두 있으면 오래된 가격 표시가 서지 않는다")
    void doesNotMarkStaleWhenEveryPriceExists() {
        holding(8101L, 25L, AssetType.STOCK, "10.000000", "700000.00");
        price(25L, "75000.0000");

        assertFalse(service.valuate(portfolio("0.00")).hasStalePrice());
    }

    @Test
    @DisplayName("총자산은 현금과 보유자산 평가액의 합이고 손익은 지급액 대비다")
    void sumsCashAndHoldingsAndComparesWithInitialAmount() {
        holding(8101L, 25L, AssetType.DEPOSIT_SAVINGS, "1.000000", "10000000.00");
        holding(8102L, 26L, AssetType.STOCK, "100.000000", "18000000.00");
        price(26L, "182000.0000");

        PortfolioValuation valuation = service.valuate(portfolio("2000000.00"));

        assertEquals(new BigDecimal("28200000.00"), valuation.getHoldingsValue());
        assertEquals(new BigDecimal("30200000.00"), valuation.getTotalAssets());
        assertEquals(new BigDecimal("200000.00"), valuation.getProfitLoss());
        assertEquals(new BigDecimal("0.67"), valuation.getProfitRate());
    }

    @Test
    @DisplayName("손실이면 손익과 손익률이 음수다")
    void reportsNegativeProfitOnLoss() {
        holding(8101L, 26L, AssetType.STOCK, "100.000000", "20000000.00");
        price(26L, "150000.0000");

        PortfolioValuation valuation = service.valuate(portfolio("10000000.00"));

        assertEquals(new BigDecimal("-5000000.00"), valuation.getProfitLoss());
        assertEquals(new BigDecimal("-16.67"), valuation.getProfitRate());
    }

    @Test
    @DisplayName("자산군 비중의 분모는 총자산이다 — 나머지가 현금 비중이 된다")
    void computesAllocationRatioAgainstTotalAssets() {
        // API_DOCS 응답 예시와 같은 값이다 (10,080,000 / 30,200,000 = 33.38%).
        holding(8101L, 25L, AssetType.DEPOSIT_SAVINGS, "1.000000", "10080000.00");
        holding(8102L, 26L, AssetType.STOCK, "100.000000", "18000000.00");
        price(26L, "181200.0000");

        List<AssetAllocation> allocations = service.valuate(portfolio("2000000.00")).getAllocations();

        assertEquals(2, allocations.size());
        assertEquals(AssetType.DEPOSIT_SAVINGS, allocations.get(0).getAssetType());
        assertEquals(new BigDecimal("33.38"), allocations.get(0).getRatio());
        assertEquals(new BigDecimal("60.00"), allocations.get(1).getRatio());
    }

    @Test
    @DisplayName("같은 자산군의 보유는 하나로 합산한다")
    void mergesHoldingsOfSameAssetType() {
        holding(8101L, 25L, AssetType.STOCK, "10.000000", "1000000.00");
        holding(8102L, 26L, AssetType.STOCK, "20.000000", "2000000.00");
        price(25L, "100000.0000");
        price(26L, "100000.0000");

        List<AssetAllocation> allocations = service.valuate(portfolio("0.00")).getAllocations();

        assertEquals(1, allocations.size());
        assertEquals(new BigDecimal("3000000.00"), allocations.get(0).getValuationAmount());
        assertEquals(new BigDecimal("100.00"), allocations.get(0).getRatio());
    }

    @Test
    @DisplayName("보유가 없으면 전액 현금이고 비중은 비어 있다")
    void handlesCashOnlyPortfolio() {
        PortfolioValuation valuation = service.valuate(portfolio("30000000.00"));

        assertEquals(new BigDecimal("0.00"), valuation.getHoldingsValue());
        assertEquals(new BigDecimal("30000000.00"), valuation.getTotalAssets());
        assertEquals(new BigDecimal("0.00"), valuation.getProfitLoss());
        assertEquals(new BigDecimal("0.00"), valuation.getProfitRate());
        assertTrue(valuation.getAllocations().isEmpty());
        assertTrue(valuation.getHoldings().isEmpty());
    }

    @Test
    @DisplayName("금액은 소수점 둘째 자리로 맞춘다")
    void keepsMoneyScaleAtTwoDecimals() {
        holding(8101L, 26L, AssetType.STOCK, "3.000000", "100000.00");
        price(26L, "33333.3333");

        PortfolioValuation valuation = service.valuate(portfolio("0.00"));

        // 33333.3333 × 3 = 99999.9999 → 100000.00
        assertEquals(new BigDecimal("100000.00"), valuation.getHoldingsValue());
        assertEquals(2, valuation.getTotalAssets().scale());
        assertEquals(2, valuation.getProfitLoss().scale());
    }

    @Test
    @DisplayName("같은 자산군이라도 상품마다 가격 기준 시점이 다를 수 있다")
    void keepsPerHoldingValuationTime() {
        LocalDateTime older = LocalDateTime.of(2026, 7, 28, 6, 0);

        holding(8101L, 25L, AssetType.STOCK, "1.000000", "100000.00");
        holding(8102L, 26L, AssetType.STOCK, "1.000000", "100000.00");
        price(25L, "100000.0000");
        price(26L, "120000.0000", older);

        PortfolioValuation valuation = service.valuate(portfolio("0.00"));

        assertEquals(PRICE_TIME, valuation.getHoldings().get(0).getValuedAt());
        assertEquals(older, valuation.getHoldings().get(1).getValuedAt());
        assertNotNull(valuation.getValuedAt(), "포트폴리오 전체의 계산 시각도 함께 알려야 합니다.");
    }

    @Test
    @DisplayName("가격이 필요한 상품만 골라서 한 번에 조회한다")
    void looksUpPricesOnceForPriceBasedProducts() {
        holding(8101L, 25L, AssetType.DEPOSIT_SAVINGS, "1.000000", "100000.00");
        holding(8102L, 26L, AssetType.STOCK, "1.000000", "100000.00");
        holding(8103L, 27L, AssetType.FUND, "1.000000", "100000.00");
        price(26L, "100000.0000");
        price(27L, "100000.0000");

        service.valuate(portfolio("0.00"));

        verify(productPriceMapper).findLatestByProductIds(Arrays.asList(26L, 27L));
    }

    @Test
    @DisplayName("평가 대상 포트폴리오가 없으면 거부한다")
    void rejectsMissingPortfolio() {
        assertThrows(IllegalArgumentException.class, () -> service.valuate(null));
        assertThrows(IllegalArgumentException.class, () -> service.valuate(new Portfolio()));
    }

    // ------------------------------------------------------------- 캐시 경유

    /** 캐시에 직접 넣는다 — 장중 폴링이 갱신해 둔 상태를 흉내 낸다. */
    private void cached(long productId, String price, LocalDateTime referenceAt) {
        ProductPrice productPrice = new ProductPrice();

        productPrice.setProductId(productId);
        productPrice.setPrice(new BigDecimal(price));
        productPrice.setReferenceAt(referenceAt);

        priceCache.put(productPrice);
    }

    @Test
    @DisplayName("장중에는 캐시의 실시간 가격으로 평가한다 — 체결가와 같은 값이어야 한다")
    void valuesWithCachedPriceDuringSession() {
        LocalDateTime live = LocalDateTime.of(2026, 8, 6, 3, 30);

        holding(8101L, 25L, AssetType.STOCK, "10.000000", "700000.00");
        cached(25L, "80000.0000", live);
        price(25L, "75000.0000");   // DB에는 종가가 남아 있다

        HoldingValuation valuation = service.valuate(portfolio("0.00")).getHoldings().get(0);

        assertEquals(new BigDecimal("800000.00"), valuation.getValuationAmount(), "캐시 값으로 평가해야 합니다.");
        assertEquals(live, valuation.getValuedAt(), "기준 시점도 캐시의 것이어야 합니다.");
        verify(productPriceMapper, never()).findLatestByProductIds(any());
    }

    @Test
    @DisplayName("캐시가 비면 DB 종가로 평가한다 — 재시작 직후에도 평가가 멈추지 않는다")
    void fallsBackToStoredClosingPrice() {
        holding(8101L, 25L, AssetType.STOCK, "10.000000", "700000.00");
        price(25L, "75000.0000");

        HoldingValuation valuation = service.valuate(portfolio("0.00")).getHoldings().get(0);

        assertEquals(new BigDecimal("750000.00"), valuation.getValuationAmount());
        assertEquals(ValuationBasis.MARKET_PRICE, valuation.getBasis());
    }

    @Test
    @DisplayName("일부만 캐시에 있으면 빠진 것만 DB에서 읽는다")
    void queriesOnlyUncachedProducts() {
        holding(8101L, 26L, AssetType.STOCK, "1.000000", "100000.00");
        holding(8102L, 27L, AssetType.FUND, "1.000000", "100000.00");
        cached(26L, "110000.0000", PRICE_TIME);
        price(27L, "120000.0000");

        PortfolioValuation valuation = service.valuate(portfolio("0.00"));

        assertEquals(new BigDecimal("110000.00"), valuation.getHoldings().get(0).getValuationAmount());
        assertEquals(new BigDecimal("120000.00"), valuation.getHoldings().get(1).getValuationAmount());
        verify(productPriceMapper).findLatestByProductIds(Arrays.asList(27L));
    }
}
