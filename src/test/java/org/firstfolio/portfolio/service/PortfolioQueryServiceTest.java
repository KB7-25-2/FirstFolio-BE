package org.firstfolio.portfolio.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.HoldingStatus;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioStatus;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioQueryServiceTest {

    private static final long USER_ID = 101L;
    private static final long PORTFOLIO_ID = 8001L;

    private PortfolioMapper portfolioMapper;
    private PortfolioTransactionMapper transactionMapper;
    private PortfolioHoldingMapper holdingMapper;
    private ProductPriceMapper productPriceMapper;
    private PortfolioQueryService service;

    private final List<PortfolioHolding> holdings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        transactionMapper = mock(PortfolioTransactionMapper.class);
        holdingMapper = mock(PortfolioHoldingMapper.class);
        productPriceMapper = mock(ProductPriceMapper.class);

        service = new PortfolioQueryService(
                portfolioMapper,
                transactionMapper,
                new PortfolioValuationService(holdingMapper, productPriceMapper)
        );

        holdings.clear();

        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(portfolio());
        when(holdingMapper.findActiveByPortfolioId(anyLong())).thenReturn(holdings);
        when(productPriceMapper.findLatestByProductIds(any())).thenReturn(List.of());
        when(transactionMapper.findPage(anyLong(), any(), any(), anyInt())).thenReturn(List.of());
    }

    private Portfolio portfolio() {
        Portfolio portfolio = new Portfolio();

        portfolio.setPortfolioId(PORTFOLIO_ID);
        portfolio.setUserId(USER_ID);
        portfolio.setGenerationNo(2);
        portfolio.setStatus(PortfolioStatus.ACTIVE);
        portfolio.setInitialAmount(new BigDecimal("30000000.00"));
        portfolio.setCashBalance(new BigDecimal("2000000.00"));

        return portfolio;
    }

    private void stockHolding() {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setHoldingId(8101L);
        holding.setPortfolioId(PORTFOLIO_ID);
        holding.setProductId(26L);
        holding.setQuantity(new BigDecimal("100.000000"));
        holding.setPrincipalAmount(new BigDecimal("18000000.00"));
        holding.setStatus(HoldingStatus.ACTIVE);
        holding.setProductDisplayName("푸른전자");
        holding.setProductAssetType(AssetType.STOCK);

        holdings.add(holding);

        ProductPrice price = new ProductPrice();

        price.setProductId(26L);
        price.setPrice(new BigDecimal("182000.0000"));
        price.setReferenceAt(LocalDateTime.of(2026, 7, 29, 3, 0));

        when(productPriceMapper.findLatestByProductIds(any())).thenReturn(List.of(price));
    }

    private PortfolioTransaction transaction(long id, TransactionType type) {
        PortfolioTransaction transaction = new PortfolioTransaction();

        transaction.setPortfolioTransactionId(id);
        transaction.setPortfolioId(PORTFOLIO_ID);
        transaction.setTransactionType(type);
        transaction.setAmount(new BigDecimal("12000.00"));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedAt(LocalDateTime.of(2026, 7, 29, 3, 10));

        return transaction;
    }

    @Test
    @DisplayName("현금·세대·평가 결과를 함께 돌려준다")
    void returnsCurrentPortfolioDetail() {
        stockHolding();

        PortfolioDetailResponse response = service.findCurrent(USER_ID);

        assertEquals(PORTFOLIO_ID, response.getPortfolioId());
        assertEquals(2, response.getGenerationNo());
        assertEquals(new BigDecimal("2000000.00"), response.getCashBalance());
        assertEquals(new BigDecimal("18200000.00"), response.getSummary().getHoldingsValue());
        assertEquals(new BigDecimal("20200000.00"), response.getSummary().getTotalAssets());
        assertEquals(new BigDecimal("-9800000.00"), response.getSummary().getProfitLoss());
    }

    @Test
    @DisplayName("보유 상품은 가명과 평가 근거를 함께 내보낸다")
    void exposesHoldingWithDisplayNameAndBasis() {
        stockHolding();

        PortfolioDetailResponse.Holding holding = service.findCurrent(USER_ID).getHoldings().get(0);

        assertEquals(8101L, holding.getHoldingId());
        assertEquals("푸른전자", holding.getDisplayName());
        assertEquals("STOCK", holding.getAssetType());
        assertEquals(new BigDecimal("18200000.00"), holding.getValuationAmount());
        assertEquals("MARKET_PRICE", holding.getValuationBasis());
        assertEquals(LocalDateTime.of(2026, 7, 29, 3, 0), holding.getValuedAt());
    }

    @Test
    @DisplayName("자산군 비중을 함께 돌려준다")
    void returnsAllocation() {
        stockHolding();

        List<PortfolioDetailResponse.Allocation> allocation = service.findCurrent(USER_ID).getAllocation();

        assertEquals(1, allocation.size());
        assertEquals("STOCK", allocation.get(0).getAssetType());
        assertEquals(new BigDecimal("90.10"), allocation.get(0).getRatio());
    }

    @Test
    @DisplayName("활성 포트폴리오가 없으면 404다")
    void rejectsWhenNoActivePortfolio() {
        when(portfolioMapper.findActiveByUserId(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> service.findCurrent(USER_ID));

        assertEquals(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("요청한 사용자의 포트폴리오만 조회한다 — 식별자를 받지 않는다")
    void looksUpOnlyRequesterPortfolio() {
        long otherUserId = 999L;

        when(portfolioMapper.findActiveByUserId(otherUserId)).thenReturn(null);

        assertThrows(ApiException.class, () -> service.findCurrent(otherUserId));

        verify(portfolioMapper).findActiveByUserId(otherUserId);
        verify(portfolioMapper, never()).findById(anyLong());
        verify(holdingMapper, never()).findActiveByPortfolioId(anyLong());
    }

    @Test
    @DisplayName("이력도 활성 포트폴리오가 없으면 404다")
    void rejectsTransactionsWhenNoActivePortfolio() {
        when(portfolioMapper.findActiveByUserId(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findCurrentTransactions(USER_ID, null, null, null)
        );

        assertEquals(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("이력은 현재 포트폴리오 세대의 것만 읽는다")
    void readsTransactionsOfCurrentGenerationOnly() {
        service.findCurrentTransactions(USER_ID, null, null, null);

        verify(transactionMapper).findPage(eq(PORTFOLIO_ID), isNull(), isNull(), anyInt());
    }

    @Test
    @DisplayName("유형 필터를 그대로 전달한다")
    void passesTypeFilter() {
        service.findCurrentTransactions(USER_ID, "interest", null, null);

        verify(transactionMapper).findPage(
                eq(PORTFOLIO_ID),
                eq(TransactionType.INTEREST),
                isNull(),
                anyInt()
        );
    }

    @Test
    @DisplayName("알 수 없는 유형 필터는 400이다")
    void rejectsUnknownTypeFilter() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findCurrentTransactions(USER_ID, "GIFT", null, null)
        );

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("숫자가 아닌 커서는 400이다")
    void rejectsNonNumericCursor() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findCurrentTransactions(USER_ID, null, "abc", null)
        );

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("다음 페이지가 있으면 마지막 식별자를 커서로 준다")
    void returnsNextCursorWhenMorePagesExist() {
        // 요청 크기 2 + 다음 페이지 확인용 1건.
        when(transactionMapper.findPage(anyLong(), any(), any(), eq(3))).thenReturn(List.of(
                transaction(8203L, TransactionType.INTEREST),
                transaction(8202L, TransactionType.BUY),
                transaction(8201L, TransactionType.INITIAL_GRANT)
        ));

        PortfolioTransactionPageResponse response =
                service.findCurrentTransactions(USER_ID, null, null, 2);

        assertEquals(2, response.getItems().size(), "요청한 크기만 돌려줘야 합니다.");
        assertEquals("8202", response.getNextCursor());
    }

    @Test
    @DisplayName("마지막 페이지면 커서가 null이다")
    void returnsNullCursorOnLastPage() {
        when(transactionMapper.findPage(anyLong(), any(), any(), anyInt())).thenReturn(List.of(
                transaction(8201L, TransactionType.INITIAL_GRANT)
        ));

        PortfolioTransactionPageResponse response =
                service.findCurrentTransactions(USER_ID, null, null, 2);

        assertEquals(1, response.getItems().size());
        assertNull(response.getNextCursor());
    }

    @Test
    @DisplayName("저장된 거래 상세 JSON을 그대로 싣는다")
    void includesStoredTransactionDetail() {
        PortfolioTransaction transaction = transaction(8201L, TransactionType.INTEREST);

        transaction.setDetailJson("{\"calculation_basis\":\"연 3.2% 12개월\"}");
        transaction.setProductDisplayName("푸른나무 정기예금");

        when(transactionMapper.findPage(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(transaction));

        PortfolioTransactionPageResponse.Item item =
                service.findCurrentTransactions(USER_ID, null, null, null).getItems().get(0);

        assertEquals("INTEREST", item.getTransactionType());
        assertEquals("푸른나무 정기예금", item.getDisplayName());
        assertEquals("연 3.2% 12개월", item.getDetail().get("calculation_basis").asText());
    }

    @Test
    @DisplayName("상세 JSON이 없으면 null로 둔다")
    void leavesDetailNullWhenNotStored() {
        when(transactionMapper.findPage(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(transaction(8201L, TransactionType.INITIAL_GRANT)));

        PortfolioTransactionPageResponse.Item item =
                service.findCurrentTransactions(USER_ID, null, null, null).getItems().get(0);

        assertNull(item.getDetail());
        assertNull(item.getDisplayName(), "지급 이력에는 상품이 없습니다.");
    }

    @Test
    @DisplayName("깨진 상세 JSON은 조용히 넘기지 않는다")
    void failsOnBrokenDetailJson() {
        PortfolioTransaction transaction = transaction(8201L, TransactionType.INTEREST);

        transaction.setDetailJson("{broken");

        when(transactionMapper.findPage(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(transaction));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findCurrentTransactions(USER_ID, null, null, null)
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("페이지 크기는 기본 20, 최대 100이다")
    void clampsPageSize() {
        service.findCurrentTransactions(USER_ID, null, null, null);
        verify(transactionMapper).findPage(anyLong(), any(), any(), eq(21));

        service.findCurrentTransactions(USER_ID, null, null, 500);
        verify(transactionMapper).findPage(anyLong(), any(), any(), eq(101));

        service.findCurrentTransactions(USER_ID, null, null, 0);
        verify(transactionMapper, org.mockito.Mockito.times(2))
                .findPage(anyLong(), any(), any(), eq(21));
    }
}
