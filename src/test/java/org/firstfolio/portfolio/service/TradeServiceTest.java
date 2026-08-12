package org.firstfolio.portfolio.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.HoldingStatus;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioStatus;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TradePolicy;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.service.CurrentPriceReader;
import org.firstfolio.simulation.service.TradingHours;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeServiceTest {

    private static final long USER_ID = 101L;
    private static final long PORTFOLIO_ID = 8001L;
    private static final long STOCK_ID = 87L;
    private static final long DEPOSIT_ID = 25L;
    private static final String KEY = "trade-101-1";

    private PortfolioMapper portfolioMapper;
    private PortfolioHoldingMapper holdingMapper;
    private PortfolioTransactionMapper transactionMapper;
    private FinancialProductMapper productMapper;
    private CurrentPriceReader priceReader;
    private AssetEventScheduler eventScheduler;
    private TradePolicyProvider tradePolicyProvider;
    private TradeService service;

    private final Map<String, PortfolioTransaction> stored = new HashMap<>();
    private PortfolioHolding currentHolding;
    private BigDecimal cash;

    /** 장중으로 고정하기 어려워, 시간 검증은 항상 열린 것으로 두고 별도 테스트에서 확인한다. */
    private final TradingHours alwaysOpen = new TradingHours() {
        @Override
        public boolean isOpen(AssetType assetType, java.time.LocalDateTime nowUtc) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        holdingMapper = mock(PortfolioHoldingMapper.class);
        transactionMapper = mock(PortfolioTransactionMapper.class);
        productMapper = mock(FinancialProductMapper.class);
        priceReader = mock(CurrentPriceReader.class);
        eventScheduler = mock(AssetEventScheduler.class);
        tradePolicyProvider = mock(TradePolicyProvider.class);

        when(tradePolicyProvider.findAt(any())).thenReturn(tradePolicy(1));

        service = new TradeService(
                portfolioMapper, holdingMapper, transactionMapper, productMapper,
                priceReader, new TradeCalculator(), alwaysOpen, eventScheduler,
                tradePolicyProvider
        );

        stored.clear();
        currentHolding = null;
        cash = new BigDecimal("30000000.00");

        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(portfolio());
        when(portfolioMapper.findById(anyLong())).thenAnswer(invocation -> portfolio());

        // 조건부 UPDATE를 흉내 낸다 — 잔액이 모자라면 0을 돌려준다.
        when(portfolioMapper.decreaseCash(anyLong(), any(), any())).thenAnswer(invocation -> {
            BigDecimal amount = invocation.getArgument(1);
            if (cash.compareTo(amount) < 0) {
                return 0;
            }
            cash = cash.subtract(amount);
            return 1;
        });
        when(portfolioMapper.increaseCash(anyLong(), any(), any())).thenAnswer(invocation -> {
            cash = cash.add((BigDecimal) invocation.getArgument(1));
            return 1;
        });

        when(productMapper.findActiveById(STOCK_ID)).thenReturn(product(STOCK_ID, AssetType.STOCK));
        when(productMapper.findActiveById(DEPOSIT_ID))
                .thenReturn(product(DEPOSIT_ID, AssetType.DEPOSIT_SAVINGS));
        when(priceReader.read(anyLong())).thenReturn(price("241500.0000"));

        when(holdingMapper.findByPortfolioAndProduct(anyLong(), anyLong()))
                .thenAnswer(invocation -> currentHolding);
        doAnswer(invocation -> {
            PortfolioHolding holding = invocation.getArgument(0);
            holding.setHoldingId(9101L);
            currentHolding = holding;
            return null;
        }).when(holdingMapper).insert(any(PortfolioHolding.class));
        when(holdingMapper.update(any(PortfolioHolding.class))).thenAnswer(invocation -> {
            currentHolding = invocation.getArgument(0);
            return 1;
        });

        when(transactionMapper.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            PortfolioTransaction tx = invocation.getArgument(0);
            tx.setPortfolioTransactionId(7001L + stored.size());
            stored.put(tx.getIdempotencyKey(), tx);
            return null;
        }).when(transactionMapper).insert(any(PortfolioTransaction.class));
    }

    private Portfolio portfolio() {
        Portfolio portfolio = new Portfolio();

        portfolio.setPortfolioId(PORTFOLIO_ID);
        portfolio.setUserId(USER_ID);
        portfolio.setGenerationNo(1);
        portfolio.setStatus(PortfolioStatus.ACTIVE);
        portfolio.setInitialAmount(new BigDecimal("30000000.00"));
        portfolio.setCashBalance(cash);

        return portfolio;
    }

    private static FinancialProduct product(long productId, AssetType assetType) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(productId);
        product.setAssetType(assetType);
        product.setActive(true);
        product.setSimulationTermsJson(assetType.isTimeCompressed() ? "{\"a\":1}" : null);

        return product;
    }

    /** v3 3.3절 확정 요율. 수수료 0.015%. */
    private static TradePolicy tradePolicy(Integer versionNo) {
        return new TradePolicy(
                new BigDecimal("0.00015"),
                new BigDecimal("0.00015"),
                new BigDecimal("0.0020"),
                new BigDecimal("0.154"),
                new BigDecimal("0.154"),
                versionNo
        );
    }

    private static ProductPrice price(String value) {
        ProductPrice price = new ProductPrice();

        price.setPrice(new BigDecimal(value));

        return price;
    }

    private void givenHolding(long productId, String quantity, String principal, HoldingStatus status) {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setHoldingId(9101L);
        holding.setPortfolioId(PORTFOLIO_ID);
        holding.setProductId(productId);
        holding.setQuantity(new BigDecimal(quantity));
        holding.setPrincipalAmount(new BigDecimal(principal));
        holding.setStatus(status);

        currentHolding = holding;
    }

    private TradeResult buy(long productId, String amount) {
        return service.trade(USER_ID, new TradeCommand(
                KEY, TransactionType.BUY, productId, new BigDecimal(amount), null));
    }

    private TradeResult sell(long productId, String quantity) {
        return service.trade(USER_ID, new TradeCommand(
                "sell-" + KEY, TransactionType.SELL, productId,
                null, quantity == null ? null : new BigDecimal(quantity)));
    }

    // ------------------------------------------------------------- 매수

    @Test
    @DisplayName("매수형 매수는 정수 주수만 사고 남은 금액은 현금에 남는다")
    void buysWholeSharesAndKeepsRemainderAsCash() {
        TradeResult result = buy(STOCK_ID, "5000000.00");

        assertEquals(new BigDecimal("5000000.00"), result.getRequestedAmount());
        assertEquals(new BigDecimal("4830000.00"), result.getAmount());
        assertEquals(new BigDecimal("20.000000"), result.getQuantity());
        // 30,000,000 − 4,830,000(체결) − 724.50(수수료)
        assertEquals(new BigDecimal("25169275.50"), result.getCashBalance(),
                "체결액과 수수료만 빠지고 차액 17만원은 현금에 남아야 합니다.");
    }

    @Test
    @DisplayName("매수하면 보유가 생기고 평균 매입 단가가 체결가로 잡힌다")
    void createsHoldingOnFirstBuy() {
        buy(STOCK_ID, "5000000.00");

        assertNotNull(currentHolding);
        assertEquals(new BigDecimal("20.000000"), currentHolding.getQuantity());
        assertEquals(new BigDecimal("4830000.00"), currentHolding.getPrincipalAmount());
        assertEquals(new BigDecimal("241500.0000"), currentHolding.getAverageCost());
        assertEquals(HoldingStatus.ACTIVE, currentHolding.getStatus());
    }

    @Test
    @DisplayName("추가 매수하면 수량이 누적되고 평균 단가가 다시 계산된다")
    void accumulatesOnAdditionalBuy() {
        givenHolding(STOCK_ID, "10.000000", "1000000.00", HoldingStatus.ACTIVE);
        when(priceReader.read(anyLong())).thenReturn(price("120000.0000"));

        buy(STOCK_ID, "1200000.00");

        assertEquals(new BigDecimal("20.000000"), currentHolding.getQuantity());
        assertEquals(new BigDecimal("2200000.00"), currentHolding.getPrincipalAmount());
        assertEquals(new BigDecimal("110000.0000"), currentHolding.getAverageCost());
    }

    @Test
    @DisplayName("전량 매도했던 상품을 다시 사면 기존 행을 되살린다")
    void revivesSoldHoldingInsteadOfInserting() {
        givenHolding(STOCK_ID, "0.000000", "0.00", HoldingStatus.SOLD);

        buy(STOCK_ID, "5000000.00");

        verify(holdingMapper, never()).insert(any());
        verify(holdingMapper).update(any(PortfolioHolding.class));
        assertEquals(HoldingStatus.ACTIVE, currentHolding.getStatus());
        assertEquals(new BigDecimal("20.000000"), currentHolding.getQuantity());
        assertEquals(new BigDecimal("4830000.00"), currentHolding.getPrincipalAmount(),
                "되살릴 때 이전 원금이 더해지면 안 됩니다.");
    }

    @Test
    @DisplayName("값이 남아 있는 보유를 되살릴 때 이전 수량·원금을 물려받지 않는다")
    void doesNotInheritValuesWhenRevivingNonActiveHolding() {
        // 만기 처리(#17)된 보유는 수량·원금이 남아 있을 수 있다. 그대로 더하면 없는 자산이 생긴다.
        givenHolding(DEPOSIT_ID, "1.000000", "10000000.00", HoldingStatus.MATURED);

        buy(DEPOSIT_ID, "5000000.00");

        assertEquals(new BigDecimal("5000000.00"), currentHolding.getPrincipalAmount(),
                "이전 원금 1천만원이 더해지면 안 됩니다.");
        assertEquals(new BigDecimal("1.000000"), currentHolding.getQuantity(),
                "이전 수량이 누적되면 안 됩니다.");
        assertEquals(HoldingStatus.ACTIVE, currentHolding.getStatus());
    }

    @Test
    @DisplayName("가입형 매수는 금액이 그대로 원금이고 수량·단가가 없다")
    void subscribesWithoutConversion() {
        TradeResult result = buy(DEPOSIT_ID, "10000000.00");

        assertEquals(new BigDecimal("10000000.00"), result.getAmount());
        assertNull(result.getQuantity());
        assertNull(result.getUnitPrice());
        assertNull(currentHolding.getAverageCost(), "가입형은 평균 단가가 없습니다.");
    }

    @Test
    @DisplayName("가입형은 이미 가입했으면 다시 살 수 없다")
    void blocksResubscribeWhileActive() {
        givenHolding(DEPOSIT_ID, "1.000000", "10000000.00", HoldingStatus.ACTIVE);

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(DEPOSIT_ID, "5000000.00"));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
        verify(portfolioMapper, never()).decreaseCash(anyLong(), any(), any());
    }

    @Test
    @DisplayName("가입형도 해지한 뒤에는 다시 가입할 수 있다")
    void allowsResubscribeAfterRedeem() {
        givenHolding(DEPOSIT_ID, "0.000000", "0.00", HoldingStatus.SOLD);

        TradeResult result = buy(DEPOSIT_ID, "5000000.00");

        assertEquals(new BigDecimal("5000000.00"), result.getAmount());
        assertEquals(HoldingStatus.ACTIVE, currentHolding.getStatus());
    }

    @Test
    @DisplayName("현금이 모자라면 거래하지 않는다")
    void rejectsWhenCashIsInsufficient() {
        cash = new BigDecimal("1000000.00");

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "5000000.00"));

        assertEquals(ErrorCode.INSUFFICIENT_SIMULATION_CASH, exception.getErrorCode());
        verify(holdingMapper, never()).insert(any());
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("요청 금액이 1주 값보다 적으면 거부한다")
    void rejectsAmountBelowOneShare() {
        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "100000.00"));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
        verify(portfolioMapper, never()).decreaseCash(anyLong(), any(), any());
    }

    @Test
    @DisplayName("기준 가격이 없으면 거래하지 않는다 — 임의 값으로 체결하지 않는다")
    void rejectsWhenPriceIsMissing() {
        when(priceReader.read(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "5000000.00"));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    @DisplayName("매수에 수량을 보내면 거부한다")
    void rejectsQuantityOnBuy() {
        ApiException exception = assertThrows(ApiException.class, () -> service.trade(
                USER_ID,
                new TradeCommand(KEY, TransactionType.BUY, STOCK_ID,
                        new BigDecimal("5000000.00"), new BigDecimal("2.000000"))
        ));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
    }

    // ------------------------------------------------------------- 매도

    @Test
    @DisplayName("매수형 부분 매도는 수량·원금을 비율만큼 줄이고 평균 단가는 유지한다")
    void reducesHoldingOnPartialSell() {
        givenHolding(STOCK_ID, "10.000000", "1000000.00", HoldingStatus.ACTIVE);
        currentHolding.setAverageCost(new BigDecimal("100000.0000"));

        TradeResult result = sell(STOCK_ID, "3.000000");

        assertEquals(new BigDecimal("724500.00"), result.getAmount(), "3주 × 241,500");
        assertEquals(new BigDecimal("7.000000"), currentHolding.getQuantity());
        assertEquals(new BigDecimal("700000.00"), currentHolding.getPrincipalAmount());
        assertEquals(new BigDecimal("100000.0000"), currentHolding.getAverageCost(),
                "판다고 매입 단가가 달라지지는 않습니다.");
        assertEquals(HoldingStatus.ACTIVE, currentHolding.getStatus());
    }

    @Test
    @DisplayName("전량 매도하면 보유가 SOLD가 되고 원금이 0이 된다")
    void marksHoldingSoldWhenFullySold() {
        givenHolding(STOCK_ID, "10.000000", "1000000.00", HoldingStatus.ACTIVE);

        sell(STOCK_ID, "10.000000");

        assertEquals(0, currentHolding.getQuantity().signum());
        assertEquals(new BigDecimal("0.00"), currentHolding.getPrincipalAmount());
        assertEquals(HoldingStatus.SOLD, currentHolding.getStatus());
    }

    @Test
    @DisplayName("보유 수량을 넘겨 팔 수 없다")
    void rejectsSellingMoreThanHeld() {
        givenHolding(STOCK_ID, "5.000000", "500000.00", HoldingStatus.ACTIVE);

        ApiException exception = assertThrows(
                ApiException.class, () -> sell(STOCK_ID, "6.000000"));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
        verify(portfolioMapper, never()).increaseCash(anyLong(), any(), any());
    }

    @Test
    @DisplayName("가입형 매도는 수량 없이 전량 해지하고 원금을 돌려준다")
    void redeemsSubscriptionInFull() {
        givenHolding(DEPOSIT_ID, "1.000000", "10000000.00", HoldingStatus.ACTIVE);

        TradeResult result = sell(DEPOSIT_ID, null);

        assertEquals(new BigDecimal("10000000.00"), result.getAmount());
        assertNull(result.getQuantity());
        assertEquals(HoldingStatus.SOLD, currentHolding.getStatus());
        assertEquals(new BigDecimal("40000000.00"), result.getCashBalance());
    }

    @Test
    @DisplayName("가입형에 수량을 보내면 거부한다 — 부분 해지를 지원하지 않는다")
    void rejectsQuantityOnSubscriptionSell() {
        givenHolding(DEPOSIT_ID, "1.000000", "10000000.00", HoldingStatus.ACTIVE);

        ApiException exception = assertThrows(
                ApiException.class, () -> sell(DEPOSIT_ID, "1.000000"));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    @DisplayName("보유하지 않은 상품은 팔 수 없다")
    void rejectsSellingUnheldProduct() {
        ApiException exception = assertThrows(
                ApiException.class, () -> sell(STOCK_ID, "1.000000"));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
    }

    // ------------------------------------------------------------- 자산 이벤트 (FUNC-041)

    @Test
    @DisplayName("사면 만기까지의 이자·만기 일정을 함께 만든다 — 원금과 가입 시각을 넘긴다")
    void schedulesAssetEventsOnBuy() {
        buy(DEPOSIT_ID, "10000000.00");

        ArgumentCaptor<BigDecimal> principal = ArgumentCaptor.forClass(BigDecimal.class);

        verify(eventScheduler).schedule(any(), any(), any(), principal.capture(), any(), any());

        assertEquals(new BigDecimal("10000000.00"), principal.getValue(),
                "가입형은 요청 금액이 그대로 원금입니다.");
    }

    @Test
    @DisplayName("일정 생성에 거래가 읽은 정책을 그대로 넘긴다 — 같은 트랜잭션에서 버전이 갈리면 안 된다")
    void passesTheSamePolicyToEventScheduler() {
        buy(DEPOSIT_ID, "10000000.00");

        ArgumentCaptor<TradePolicy> policy = ArgumentCaptor.forClass(TradePolicy.class);

        verify(eventScheduler).schedule(any(), any(), any(), any(), any(), policy.capture());

        assertEquals(new BigDecimal("0.154"), policy.getValue().getInterestIncomeTaxRate());
        assertEquals(1, policy.getValue().getPolicyVersion());
        // 거래당 한 번만 읽으므로 이력의 수수료와 예정 이벤트의 세금이 같은 버전을 쓴다.
        verify(tradePolicyProvider, times(1)).findAt(any());
    }

    @Test
    @DisplayName("팔 때는 일정을 만들지 않는다")
    void doesNotScheduleOnSell() {
        buy(DEPOSIT_ID, "10000000.00");
        sell(DEPOSIT_ID, null);

        // 매수에서 한 번만 불렸어야 한다.
        verify(eventScheduler).schedule(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("전량 해지하면 남은 예정 이벤트를 취소한다 — 판 상품의 이자가 들어오면 안 된다")
    void cancelsScheduledEventsWhenHoldingCloses() {
        buy(DEPOSIT_ID, "10000000.00");
        sell(DEPOSIT_ID, null);

        verify(transactionMapper).cancelScheduledByHolding(9101L);
    }

    @Test
    @DisplayName("부분 매도로 보유가 남으면 예정 이벤트를 건드리지 않는다")
    void keepsScheduledEventsOnPartialSell() {
        buy(STOCK_ID, "5000000.00");
        sell(STOCK_ID, "10.000000");

        verify(transactionMapper, never()).cancelScheduledByHolding(anyLong());
    }

    // ------------------------------------------------------------- 공통

    @Test
    @DisplayName("거래 시간이 아니면 거부한다")
    void rejectsOutsideTradingHours() {
        TradeService closed = new TradeService(
                portfolioMapper, holdingMapper, transactionMapper, productMapper,
                priceReader, new TradeCalculator(),
                new TradingHours() {
                    @Override
                    public boolean isOpen(AssetType assetType, java.time.LocalDateTime nowUtc) {
                        return false;
                    }
                },
                eventScheduler,
                tradePolicyProvider
        );

        ApiException exception = assertThrows(ApiException.class, () -> closed.trade(
                USER_ID,
                new TradeCommand(KEY, TransactionType.BUY, STOCK_ID, new BigDecimal("5000000.00"), null)
        ));

        assertEquals(ErrorCode.TRADE_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    @DisplayName("활성 포트폴리오가 없으면 404다")
    void rejectsWhenNoActivePortfolio() {
        when(portfolioMapper.findActiveByUserId(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "5000000.00"));

        assertEquals(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("비공개 상품은 거래할 수 없다")
    void rejectsInactiveProduct() {
        when(productMapper.findActiveById(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "5000000.00"));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("같은 키로 같은 내용을 다시 보내면 두 번 체결하지 않는다")
    void replaysOnSameRequest() {
        TradeResult first = buy(STOCK_ID, "5000000.00");
        TradeResult again = buy(STOCK_ID, "5000000.00");

        assertEquals(first.getPortfolioTransactionId(), again.getPortfolioTransactionId());
        assertEquals(first.getAmount(), again.getAmount());
        assertEquals(first.getRequestedAmount(), again.getRequestedAmount());
        assertEquals(1, stored.size(), "거래가 하나만 기록돼야 합니다.");
        assertEquals(new BigDecimal("25169275.50"), cash,
                "현금이 두 번 빠지면 안 됩니다 — 수수료도 마찬가지다.");
    }

    @Test
    @DisplayName("같은 키로 다른 내용을 보내면 409다")
    void rejectsSameKeyWithDifferentRequest() {
        buy(STOCK_ID, "5000000.00");

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "3000000.00"));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 이력에 체결 내용과 요청 스냅샷을 남긴다")
    void recordsTransactionWithSnapshot() {
        buy(STOCK_ID, "5000000.00");

        ArgumentCaptor<PortfolioTransaction> captor =
                ArgumentCaptor.forClass(PortfolioTransaction.class);
        verify(transactionMapper).insert(captor.capture());

        PortfolioTransaction record = captor.getValue();

        assertEquals(TransactionType.BUY, record.getTransactionType());
        assertEquals(new BigDecimal("4830000.00"), record.getAmount());
        assertEquals(new BigDecimal("20.000000"), record.getQuantity());
        assertEquals(new BigDecimal("241500.0000"), record.getUnitPrice());
        assertEquals(9101L, record.getHoldingId(), "생성된 보유와 이어져야 합니다.");
        assertEquals("COMPLETED", record.getStatus().name());
        assertNotNull(record.getDetailJson());
        assertEquals(true, record.getDetailJson().contains("\"requested_amount\":\"5000000.00\""));
    }

    // ------------------------------------------------------------- 수수료

    @Test
    @DisplayName("매수는 체결액에 더해 수수료까지 현금에서 뺀다")
    void chargesBuyFeeOnTopOfExecutedAmount() {
        buy(STOCK_ID, "5000000.00");

        // 4,830,000 × 0.00015 = 724.50
        assertEquals(new BigDecimal("25169275.50"), cash);
    }

    @Test
    @DisplayName("보유 원금에는 수수료가 들어가지 않는다 — 비용이지 매입원가가 아니다")
    void keepsFeeOutOfPrincipal() {
        buy(STOCK_ID, "5000000.00");

        assertEquals(new BigDecimal("4830000.00"), currentHolding.getPrincipalAmount());
    }

    @Test
    @DisplayName("매도는 대금에서 수수료와 증권거래세를 빼고 현금에 넣는다")
    void deductsSellCostsFromProceeds() {
        givenHolding(STOCK_ID, "8.000000", "1932000.00", HoldingStatus.ACTIVE);
        cash = new BigDecimal("0.00");

        sell(STOCK_ID, "8.000000");

        // 1,932,000 − 289.80(수수료) − 3,864.00(거래세)
        assertEquals(new BigDecimal("1927846.20"), cash);
    }

    @Test
    @DisplayName("잔액을 전부 넣는 매수는 수수료만큼 모자라 거부된다")
    void rejectsBuyWhenCashCoversOnlyTheExecutedAmount() {
        // 20주 체결액과 정확히 같은 현금. 수수료 724.50원이 모자란다.
        cash = new BigDecimal("4830000.00");

        ApiException exception = assertThrows(
                ApiException.class, () -> buy(STOCK_ID, "4830000.00"));

        assertEquals(ErrorCode.INSUFFICIENT_SIMULATION_CASH, exception.getErrorCode());
    }

    @Test
    @DisplayName("수수료까지 낼 현금이 있으면 같은 요청이 체결된다 — 경계가 정확히 수수료다")
    void acceptsSameBuyWhenCashAlsoCoversTheFee() {
        cash = new BigDecimal("4830724.50");

        TradeResult result = buy(STOCK_ID, "4830000.00");

        assertEquals(new BigDecimal("4830000.00"), result.getAmount());
        assertEquals(new BigDecimal("0.00"), cash, "수수료까지 정확히 다 쓴다.");
    }

    @Test
    @DisplayName("가입형은 수수료가 없어 현금 차감이 원금과 정확히 같다")
    void chargesNoFeeOnSubscription() {
        buy(DEPOSIT_ID, "10000000.00");

        assertEquals(new BigDecimal("20000000.00"), cash);
    }

    @Test
    @DisplayName("이력에 수수료 금액과 적용 요율·정책 버전을 남긴다")
    void recordsFeeBasisInDetail() {
        buy(STOCK_ID, "5000000.00");

        String detail = stored.get(KEY).getDetailJson();

        assertEquals(true, detail.contains("\"fee_amount\":\"724.50\""), detail);
        assertEquals(true, detail.contains("\"fee_rate\":\"0.00015\""), detail);
        assertEquals(true, detail.contains("\"net_cash_amount\":\"4830724.50\""), detail);
        assertEquals(true, detail.contains("\"policy_version\":1"), detail);
    }

    @Test
    @DisplayName("설정 기본값으로 계산하면 정책 버전이 null로 남는다")
    void recordsNullPolicyVersionWhenFellBackToDefaults() {
        when(tradePolicyProvider.findAt(any())).thenReturn(tradePolicy(null));

        buy(STOCK_ID, "5000000.00");

        String detail = stored.get(KEY).getDetailJson();

        assertEquals(true, detail.contains("\"policy_version\":null"), detail);
    }

    @Test
    @DisplayName("요율은 거래 한 건에 한 번만 읽는다 — 같은 거래에서 버전이 갈리면 안 된다")
    void readsPolicyOncePerTrade() {
        buy(STOCK_ID, "5000000.00");

        verify(tradePolicyProvider, times(1)).findAt(any());
    }

    @Test
    @DisplayName("응답에 수수료와 실제 현금 증감을 함께 돌려준다")
    void returnsFeeAndNetCashInResult() {
        TradeResult result = buy(STOCK_ID, "5000000.00");

        assertEquals(new BigDecimal("4830000.00"), result.getAmount(), "체결액");
        assertEquals(new BigDecimal("724.50"), result.getFeeAmount());
        assertEquals(new BigDecimal("4830724.50"), result.getNetCashAmount(),
                "화면이 '얼마 나갔는지'로 보여줄 값입니다.");
    }

    @Test
    @DisplayName("매도 응답의 현금 증감은 대금에서 수수료와 거래세를 뺀 값이다")
    void returnsNetProceedsOnSell() {
        givenHolding(STOCK_ID, "8.000000", "1932000.00", HoldingStatus.ACTIVE);

        TradeResult result = sell(STOCK_ID, "8.000000");

        assertEquals(new BigDecimal("1932000.00"), result.getAmount());
        assertEquals(new BigDecimal("289.80"), result.getFeeAmount());
        assertEquals(new BigDecimal("3864.00"), result.getTaxAmount());
        assertEquals(new BigDecimal("1927846.20"), result.getNetCashAmount());
    }

    @Test
    @DisplayName("매수 응답의 증권거래세는 0이다 — null이 아니다")
    void returnsZeroTaxOnBuy() {
        TradeResult result = buy(STOCK_ID, "5000000.00");

        assertEquals(new BigDecimal("0.00"), result.getTaxAmount());
    }

    @Test
    @DisplayName("매도 이력에 증권거래세 금액과 적용 세율을 남긴다")
    void recordsTransactionTaxBasisInDetail() {
        givenHolding(STOCK_ID, "8.000000", "1932000.00", HoldingStatus.ACTIVE);

        sell(STOCK_ID, "8.000000");

        String detail = stored.get("sell-" + KEY).getDetailJson();

        assertEquals(true, detail.contains("\"tax_amount\":\"3864.00\""), detail);
        assertEquals(true, detail.contains("\"tax_rate\":\"0.0020\""), detail);
    }

    @Test
    @DisplayName("매수 이력에는 거래세가 0으로 남는다")
    void recordsZeroTaxOnBuy() {
        buy(STOCK_ID, "5000000.00");

        String detail = stored.get(KEY).getDetailJson();

        assertEquals(true, detail.contains("\"tax_amount\":\"0.00\""), detail);
    }

    @Test
    @DisplayName("멱등 재요청은 이력에 남긴 수수료를 그대로 돌려준다 — 지금 요율로 다시 계산하지 않는다")
    void replayRestoresRecordedFee() {
        TradeResult first = buy(STOCK_ID, "5000000.00");

        // 그 사이 요율이 바뀌어도 이미 체결된 거래의 값은 달라지면 안 된다.
        when(tradePolicyProvider.findAt(any())).thenReturn(new TradePolicy(
                new BigDecimal("0.005"), new BigDecimal("0.005"),
                new BigDecimal("0.0020"), new BigDecimal("0.154"), new BigDecimal("0.154"), 2
        ));

        TradeResult again = buy(STOCK_ID, "5000000.00");

        assertEquals(first.getFeeAmount(), again.getFeeAmount());
        assertEquals(first.getNetCashAmount(), again.getNetCashAmount());
        assertEquals(new BigDecimal("724.50"), again.getFeeAmount());
    }

    @Test
    @DisplayName("매도 멱등 재요청도 이력에 남긴 증권거래세를 그대로 돌려준다")
    void replayRestoresRecordedTax() {
        givenHolding(STOCK_ID, "8.000000", "1932000.00", HoldingStatus.ACTIVE);

        TradeResult first = sell(STOCK_ID, "8.000000");
        TradeResult again = sell(STOCK_ID, "8.000000");

        assertEquals(first.getTaxAmount(), again.getTaxAmount());
        assertEquals(new BigDecimal("3864.00"), again.getTaxAmount());
        assertEquals(new BigDecimal("1927846.20"), again.getNetCashAmount());
    }

    @Test
    @DisplayName("수수료 도입 전에 쌓인 이력을 다시 요청해도 깨지지 않는다")
    void replaysLegacyRecordWithoutFeeKeys() {
        PortfolioTransaction legacy = new PortfolioTransaction();

        legacy.setPortfolioTransactionId(7000L);
        legacy.setPortfolioId(PORTFOLIO_ID);
        legacy.setProductId(STOCK_ID);
        legacy.setTransactionType(TransactionType.BUY);
        legacy.setAmount(new BigDecimal("4830000.00"));
        legacy.setStatus(TransactionStatus.COMPLETED);
        legacy.setIdempotencyKey(KEY);
        legacy.setDetailJson("{\"request\":{\"transaction_type\":\"BUY\",\"product_id\":87,"
                + "\"amount\":\"5000000.00\",\"quantity\":null},"
                + "\"requested_amount\":\"5000000.00\",\"executed_amount\":\"4830000.00\"}");

        stored.put(KEY, legacy);

        TradeResult result = buy(STOCK_ID, "5000000.00");

        assertEquals(new BigDecimal("0.00"), result.getFeeAmount(),
                "그때 거래는 실제로 수수료가 0이었습니다.");
        assertEquals(new BigDecimal("0.00"), result.getTaxAmount(),
                "거래세도 마찬가지입니다.");
        assertEquals(new BigDecimal("4830000.00"), result.getNetCashAmount(),
                "그때는 현금 증감이 곧 체결액이었습니다.");
    }
}
