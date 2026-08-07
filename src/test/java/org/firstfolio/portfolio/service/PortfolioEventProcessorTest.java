package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.HoldingStatus;
import org.firstfolio.portfolio.domain.PortfolioHolding;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 자산 이벤트 한 건의 반영 (FUNC-041). */
class PortfolioEventProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

    private PortfolioMapper portfolioMapper;
    private PortfolioHoldingMapper holdingMapper;
    private PortfolioTransactionMapper transactionMapper;
    private PortfolioEventProcessor processor;

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        holdingMapper = mock(PortfolioHoldingMapper.class);
        transactionMapper = mock(PortfolioTransactionMapper.class);
        processor = new PortfolioEventProcessor(portfolioMapper, holdingMapper, transactionMapper);

        when(transactionMapper.markCompleted(anyLong(), any(), any())).thenReturn(1);
        when(portfolioMapper.increaseCash(anyLong(), any(), any())).thenReturn(1);
        when(holdingMapper.findByPortfolioAndProduct(anyLong(), anyLong()))
                .thenReturn(holding(HoldingStatus.ACTIVE));
    }

    @Test
    @DisplayName("이자를 현금에 더하고 보유는 그대로 둔다")
    void addsInterestToCash() {
        AssetEventOutcome outcome = processor.apply(event(TransactionType.INTEREST));

        assertEquals(AssetEventOutcome.COMPLETED, outcome);
        verify(portfolioMapper).increaseCash(eq(8001L), eq(new BigDecimal("10000.00")), any());
        verify(holdingMapper, never()).update(any());
    }

    @Test
    @DisplayName("만기는 원금을 현금으로 돌려주고 보유를 MATURED로 닫는다")
    void closesHoldingOnMaturity() {
        processor.apply(event(TransactionType.MATURITY));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);

        verify(holdingMapper).update(captor.capture());

        PortfolioHolding closed = captor.getValue();

        assertEquals(HoldingStatus.MATURED, closed.getStatus());
        assertEquals(0, closed.getPrincipalAmount().signum(),
                "원금을 남겨 두면 현금과 보유에서 이중으로 잡힙니다.");
        assertEquals(0, closed.getQuantity().signum());
    }

    @Test
    @DisplayName("이미 처리된 이벤트는 현금에 손대지 않고 건너뛴다")
    void skipsAlreadyCompletedEvent() {
        when(transactionMapper.markCompleted(anyLong(), any(), any())).thenReturn(0);

        AssetEventOutcome outcome = processor.apply(event(TransactionType.INTEREST));

        assertEquals(AssetEventOutcome.SKIPPED, outcome);
        verify(portfolioMapper, never()).increaseCash(anyLong(), any(), any());
        verify(holdingMapper, never()).update(any());
    }

    @Test
    @DisplayName("보유가 살아 있지 않으면 실패시킨다 — 판 상품에 이자를 넣지 않는다")
    void failsWhenHoldingIsNotActive() {
        when(holdingMapper.findByPortfolioAndProduct(anyLong(), anyLong()))
                .thenReturn(holding(HoldingStatus.SOLD));

        assertThrows(IllegalStateException.class,
                () -> processor.apply(event(TransactionType.INTEREST)));

        verify(portfolioMapper, never()).increaseCash(anyLong(), any(), any());
    }

    @Test
    @DisplayName("세대가 닫혀 있으면 실패시킨다")
    void failsWhenPortfolioIsNotActive() {
        when(portfolioMapper.increaseCash(anyLong(), any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> processor.apply(event(TransactionType.MATURITY)));

        verify(holdingMapper, never()).update(any());
    }

    @Test
    @DisplayName("예정 시점의 계산 근거를 남긴 채 처리 시각을 덧붙인다")
    void keepsCalculationBasisAndAddsProcessedAt() {
        processor.apply(event(TransactionType.INTEREST));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);

        verify(transactionMapper).markCompleted(anyLong(), any(), detail.capture());

        assertTrue(detail.getValue().contains("SIMPLE_INTEREST"), "계산 근거가 남아야 합니다.");
        assertTrue(detail.getValue().contains("processed_at"));
    }

    @Test
    @DisplayName("실패 사유를 근거와 함께 남긴다")
    void recordsFailureReason() {
        processor.recordFailure(
                event(TransactionType.INTEREST), new IllegalStateException("보유하지 않은 상품"));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);

        verify(transactionMapper).markFailed(anyLong(), detail.capture());

        assertTrue(detail.getValue().contains("보유하지 않은 상품"));
        assertTrue(detail.getValue().contains("SIMPLE_INTEREST"), "근거를 지우면 안 됩니다.");
    }

    // ------------------------------------------------------------------ 준비

    private static PortfolioTransaction event(TransactionType type) {
        PortfolioTransaction event = new PortfolioTransaction();

        event.setPortfolioTransactionId(7001L);
        event.setPortfolioId(8001L);
        event.setHoldingId(9101L);
        event.setProductId(25L);
        event.setTransactionType(type);
        event.setAmount(new BigDecimal("10000.00"));
        event.setStatus(TransactionStatus.SCHEDULED);
        event.setScheduledAt(NOW.minusHours(1));
        event.setEventKey(type.name().toLowerCase() + "-9101-7000-20260807T0300Z");
        event.setDetailJson("{\"basis\":\"SIMPLE_INTEREST\",\"period_months\":12}");

        return event;
    }

    private static PortfolioHolding holding(HoldingStatus status) {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setHoldingId(9101L);
        holding.setPortfolioId(8001L);
        holding.setProductId(25L);
        holding.setQuantity(new BigDecimal("1.000000"));
        holding.setPrincipalAmount(new BigDecimal("10000000.00"));
        holding.setStatus(status);

        return holding;
    }
}
