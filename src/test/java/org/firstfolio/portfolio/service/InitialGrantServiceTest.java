package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioStatus;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialGrantServiceTest {

    private static final long USER_ID = 101L;
    private static final long CURRICULUM_ITEM_ID = 7L;

    private PortfolioMapper portfolioMapper;
    private PortfolioTransactionMapper transactionMapper;
    private InitialGrantService service;

    /** 실제 DB처럼 idempotency_key로 저장·조회하는 가짜 저장소. */
    private Map<String, PortfolioTransaction> stored;
    private List<Portfolio> insertedPortfolios;

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        transactionMapper = mock(PortfolioTransactionMapper.class);
        service = new InitialGrantService(portfolioMapper, transactionMapper);

        stored = new HashMap<>();
        insertedPortfolios = new ArrayList<>();

        // insert 시 생성 키를 채워 준다 (useGeneratedKeys 흉내).
        doAnswer(invocation -> {
            Portfolio portfolio = invocation.getArgument(0);
            portfolio.setPortfolioId(8001L + insertedPortfolios.size());
            insertedPortfolios.add(portfolio);
            return null;
        }).when(portfolioMapper).insert(any(Portfolio.class));

        doAnswer(invocation -> {
            PortfolioTransaction tx = invocation.getArgument(0);
            stored.put(tx.getIdempotencyKey(), tx);
            return null;
        }).when(transactionMapper).insert(any(PortfolioTransaction.class));

        when(transactionMapper.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
    }

    private InitialGrantResult grant() {
        return service.grantOnFoundationCompleted(USER_ID, CURRICULUM_ITEM_ID);
    }

    @Test
    @DisplayName("3천만원을 지급하고 최초 포트폴리오를 만든다")
    void grantsInitialCashAndCreatesPortfolio() {
        InitialGrantResult result = grant();

        assertTrue(result.isGranted());
        assertEquals(new BigDecimal("30000000.00"), result.getAmount());
        assertNotNull(result.getPortfolioId());

        Portfolio portfolio = insertedPortfolios.get(0);

        assertEquals(USER_ID, portfolio.getUserId());
        assertEquals(1, portfolio.getGenerationNo());
        assertEquals(PortfolioStatus.ACTIVE, portfolio.getStatus());
        assertEquals(new BigDecimal("30000000.00"), portfolio.getInitialAmount());
    }

    @Test
    @DisplayName("지급 직후 포트폴리오는 전액 현금이다 — 상품 매수는 거래 API에서 한다")
    void newPortfolioHoldsOnlyCash() {
        grant();

        Portfolio portfolio = insertedPortfolios.get(0);

        assertEquals(
                portfolio.getInitialAmount(),
                portfolio.getCashBalance(),
                "배분 전이므로 현금이 지급액과 같아야 합니다."
        );
    }

    @Test
    @DisplayName("지급 이력을 INITIAL_GRANT 거래로 남긴다")
    void recordsGrantTransaction() {
        grant();

        ArgumentCaptor<PortfolioTransaction> captor =
                ArgumentCaptor.forClass(PortfolioTransaction.class);
        verify(transactionMapper).insert(captor.capture());

        PortfolioTransaction tx = captor.getValue();

        assertEquals(TransactionType.INITIAL_GRANT, tx.getTransactionType());
        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(new BigDecimal("30000000.00"), tx.getAmount());
        assertNotNull(tx.getProcessedAt());
    }

    @Test
    @DisplayName("같은 사용자·커리큘럼으로 다시 불러도 두 번 지급하지 않는다")
    void grantsOnlyOnce() {
        InitialGrantResult first = grant();
        InitialGrantResult second = grant();
        InitialGrantResult third = grant();

        assertTrue(first.isGranted());
        assertFalse(second.isGranted(), "두 번째 호출은 지급이 아니어야 합니다.");
        assertFalse(third.isGranted());

        assertEquals(1, insertedPortfolios.size(), "포트폴리오가 하나만 생겨야 합니다.");
        verify(transactionMapper, times(1)).insert(any(PortfolioTransaction.class));
    }

    @Test
    @DisplayName("이미 지급된 경우에도 기존 포트폴리오를 알려준다 — 구성 화면으로 이동해야 한다")
    void returnsExistingPortfolioWhenAlreadyGranted() {
        Long portfolioId = grant().getPortfolioId();
        InitialGrantResult again = grant();

        assertFalse(again.isGranted());
        assertEquals(portfolioId, again.getPortfolioId());
        assertEquals(new BigDecimal("30000000.00"), again.getAmount());
    }

    @Test
    @DisplayName("멱등 키는 사용자와 커리큘럼 항목으로 만든다")
    void buildsIdempotencyKeyFromUserAndCurriculumItem() {
        assertEquals(
                "initial-grant:101:7",
                InitialGrantService.idempotencyKey(USER_ID, CURRICULUM_ITEM_ID)
        );
    }

    @Test
    @DisplayName("사용자가 다르면 각자 지급받는다")
    void grantsSeparatelyPerUser() {
        service.grantOnFoundationCompleted(101L, 7L);
        service.grantOnFoundationCompleted(202L, 7L);

        assertEquals(2, insertedPortfolios.size());
    }

    @Test
    @DisplayName("동시 호출로 유니크 제약에 걸리면 기존 지급 결과를 돌려준다")
    void handlesConcurrentGrant() {
        // 조회는 비어 있는데 insert에서 충돌하는 상황 (다른 스레드가 먼저 커밋).
        PortfolioTransaction winner = new PortfolioTransaction();
        winner.setPortfolioId(9001L);
        winner.setAmount(new BigDecimal("30000000.00"));

        when(transactionMapper.findByIdempotencyKey(anyString()))
                .thenReturn(null)
                .thenReturn(winner);
        doThrow(new DuplicateKeyException("uq_portfolio_transactions_idempotency"))
                .when(transactionMapper).insert(any(PortfolioTransaction.class));

        InitialGrantResult result = grant();

        assertFalse(result.isGranted(), "먼저 커밋한 쪽의 결과를 따라야 합니다.");
        assertEquals(9001L, result.getPortfolioId());
    }

    @Test
    @DisplayName("충돌인데 기존 기록도 없으면 감추지 않고 예외를 올린다")
    void rethrowsWhenDuplicateButNothingStored() {
        when(transactionMapper.findByIdempotencyKey(anyString())).thenReturn(null);
        doThrow(new DuplicateKeyException("unexpected"))
                .when(transactionMapper).insert(any(PortfolioTransaction.class));

        assertThrows(DuplicateKeyException.class, this::grant);
    }

    @Test
    @DisplayName("필수 인자가 없으면 거부한다")
    void rejectsMissingArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.grantOnFoundationCompleted(null, CURRICULUM_ITEM_ID)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.grantOnFoundationCompleted(USER_ID, null)
        );

        verify(portfolioMapper, never()).insert(any());
    }
}
