package org.firstfolio.portfolio.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioStatus;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioResetServiceTest {

    private static final long USER_ID = 101L;
    private static final long OLD_PORTFOLIO_ID = 8001L;
    private static final String KEY = "reset-101-2";

    private PortfolioMapper portfolioMapper;
    private PortfolioHoldingMapper holdingMapper;
    private PortfolioTransactionMapper transactionMapper;
    private PortfolioResetService service;

    private final Map<String, PortfolioTransaction> stored = new HashMap<>();
    private final List<Portfolio> inserted = new ArrayList<>();
    private final List<PortfolioHolding> holdings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        holdingMapper = mock(PortfolioHoldingMapper.class);
        transactionMapper = mock(PortfolioTransactionMapper.class);
        service = new PortfolioResetService(portfolioMapper, holdingMapper, transactionMapper);

        stored.clear();
        inserted.clear();
        holdings.clear();

        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(activePortfolio());
        when(portfolioMapper.closeGeneration(anyLong(), any())).thenReturn(1);
        when(holdingMapper.findActiveByPortfolioId(anyLong())).thenReturn(holdings);
        when(transactionMapper.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> stored.get(invocation.getArgument(0)));

        doAnswer(invocation -> {
            Portfolio portfolio = invocation.getArgument(0);
            portfolio.setPortfolioId(9001L + inserted.size());
            inserted.add(portfolio);
            return null;
        }).when(portfolioMapper).insert(any(Portfolio.class));

        doAnswer(invocation -> {
            PortfolioTransaction tx = invocation.getArgument(0);
            tx.setPortfolioTransactionId(7001L + stored.size());
            stored.put(tx.getIdempotencyKey(), tx);
            return null;
        }).when(transactionMapper).insert(any(PortfolioTransaction.class));

        when(portfolioMapper.findById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            for (Portfolio portfolio : inserted) {
                if (portfolio.getPortfolioId() == id) {
                    return portfolio;
                }
            }
            return null;
        });
    }

    private Portfolio activePortfolio() {
        Portfolio portfolio = new Portfolio();

        portfolio.setPortfolioId(OLD_PORTFOLIO_ID);
        portfolio.setUserId(USER_ID);
        portfolio.setGenerationNo(1);
        portfolio.setStatus(PortfolioStatus.ACTIVE);
        portfolio.setInitialAmount(new BigDecimal("30000000.00"));
        portfolio.setCashBalance(new BigDecimal("1250000.00"));

        return portfolio;
    }

    private void givenHolding(long holdingId) {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setHoldingId(holdingId);
        holding.setPortfolioId(OLD_PORTFOLIO_ID);

        holdings.add(holding);
    }

    private PortfolioResetResult reset() {
        return service.reset(USER_ID, PortfolioResetService.RESET_CONFIRMATION, KEY);
    }

    @Test
    @DisplayName("세대를 닫고 3천만원짜리 새 세대를 만든다")
    void closesGenerationAndOpensNewOne() {
        PortfolioResetResult result = reset();

        assertEquals(OLD_PORTFOLIO_ID, result.getClosedPortfolioId());
        assertEquals(2, result.getGenerationNo(), "세대 번호가 하나 올라가야 합니다.");
        assertEquals(new BigDecimal("30000000.00"), result.getCashBalance());
        assertNotNull(result.getNewPortfolioId());
        assertNotNull(result.getResetTransactionId());

        Portfolio created = inserted.get(0);

        assertEquals(USER_ID, created.getUserId());
        assertEquals(PortfolioStatus.ACTIVE, created.getStatus());
        assertEquals(new BigDecimal("30000000.00"), created.getInitialAmount());
        assertEquals(created.getInitialAmount(), created.getCashBalance(), "새 세대는 전액 현금입니다.");
    }

    @Test
    @DisplayName("보유 상품 상태는 건드리지 않는다 — 판 적이 없다")
    void doesNotTouchHoldings() {
        givenHolding(8101L);
        givenHolding(8102L);

        reset();

        // 조회는 하지만(이력에 남길 개수) 상태를 바꾸는 메서드는 존재하지도 않는다.
        verify(holdingMapper).findActiveByPortfolioId(OLD_PORTFOLIO_ID);
        assertEquals(2, holdings.size(), "보유 목록이 그대로 남아야 합니다.");
    }

    @Test
    @DisplayName("초기화 이력을 새 세대에 RESET으로 남긴다")
    void recordsResetOnNewGeneration() {
        reset();

        ArgumentCaptor<PortfolioTransaction> captor =
                ArgumentCaptor.forClass(PortfolioTransaction.class);
        verify(transactionMapper).insert(captor.capture());

        PortfolioTransaction record = captor.getValue();

        assertEquals(TransactionType.RESET, record.getTransactionType());
        assertEquals(TransactionStatus.COMPLETED, record.getStatus());
        assertEquals(new BigDecimal("30000000.00"), record.getAmount());
        assertEquals(inserted.get(0).getPortfolioId(), record.getPortfolioId(),
                "이력은 새 세대에 남아야 합니다 — 새 출발의 첫 기록입니다.");
    }

    @Test
    @DisplayName("초기화 직전 상태를 이력에 남긴다 — 무엇을 지웠는지 아는 유일한 기록")
    void keepsSnapshotOfClosedGeneration() {
        givenHolding(8101L);
        givenHolding(8102L);
        givenHolding(8103L);

        reset();

        String detail = stored.get(KEY).getDetailJson();

        assertTrue(detail.contains("\"previous_portfolio_id\":8001"), detail);
        assertTrue(detail.contains("\"previous_generation_no\":1"), detail);
        assertTrue(detail.contains("\"previous_cash_balance\":\"1250000.00\""), detail);
        assertTrue(detail.contains("\"previous_holding_count\":3"), detail);
    }

    @Test
    @DisplayName("같은 키로 다시 부르면 초기화하지 않고 기존 결과를 돌려준다")
    void replaysInsteadOfResettingAgain() {
        PortfolioResetResult first = reset();
        PortfolioResetResult again = reset();

        assertEquals(first.getNewPortfolioId(), again.getNewPortfolioId());
        assertEquals(first.getClosedPortfolioId(), again.getClosedPortfolioId());
        assertEquals(first.getResetTransactionId(), again.getResetTransactionId());
        assertEquals(first.getGenerationNo(), again.getGenerationNo());

        assertEquals(1, inserted.size(), "세대가 하나만 생겨야 합니다.");
        verify(portfolioMapper, times(1)).closeGeneration(anyLong(), any());
    }

    @Test
    @DisplayName("확인 문구가 다르면 초기화하지 않는다")
    void rejectsWrongConfirmation() {
        for (String wrong : new String[]{"", "reset_portfolio", "RESET", "초기화", null}) {
            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> service.reset(USER_ID, wrong, KEY)
            );

            assertEquals(ErrorCode.RESET_CONFIRMATION_REQUIRED, exception.getErrorCode());
        }

        verify(portfolioMapper, never()).closeGeneration(anyLong(), any());
        verify(portfolioMapper, never()).insert(any());
    }

    @Test
    @DisplayName("멱등 키가 없으면 거부한다")
    void rejectsMissingIdempotencyKey() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.reset(USER_ID, PortfolioResetService.RESET_CONFIRMATION, "  ")
        );

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(portfolioMapper, never()).closeGeneration(anyLong(), any());
    }

    @Test
    @DisplayName("활성 포트폴리오가 없으면 404다")
    void rejectsWhenNoActivePortfolio() {
        when(portfolioMapper.findActiveByUserId(anyLong())).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, this::reset);

        assertEquals(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND, exception.getErrorCode());
        verify(portfolioMapper, never()).insert(any());
    }

    @Test
    @DisplayName("다른 요청이 먼저 세대를 닫았으면 두 번 초기화하지 않는다")
    void rejectsWhenGenerationAlreadyClosed() {
        // 조건부 UPDATE가 0을 돌려주는 상황 — 조회와 갱신 사이에 다른 요청이 끼어들었다.
        when(portfolioMapper.closeGeneration(anyLong(), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, this::reset);

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, exception.getErrorCode());
        verify(portfolioMapper, never()).insert(any());
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("초기화 횟수를 제한하지 않는다 — 학습자가 원할 때 다시 시작한다")
    void allowsUnlimitedResets() {
        service.reset(USER_ID, PortfolioResetService.RESET_CONFIRMATION, "reset-101-2");

        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(inserted.get(0));
        service.reset(USER_ID, PortfolioResetService.RESET_CONFIRMATION, "reset-101-3");

        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(inserted.get(1));
        service.reset(USER_ID, PortfolioResetService.RESET_CONFIRMATION, "reset-101-4");

        assertEquals(3, inserted.size());
        assertEquals(4, inserted.get(2).getGenerationNo(), "세대가 계속 올라가야 합니다.");
    }
}
