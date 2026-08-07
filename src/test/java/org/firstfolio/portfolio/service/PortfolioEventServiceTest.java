package org.firstfolio.portfolio.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배치 루프 (FUNC-041).
 *
 * <p>여기서 확인하는 것은 <b>집계와 실패 격리의 흐름</b>이다. 트랜잭션이 실제로 건별로 끊기는지는
 * 프록시와 DB가 있어야 드러나므로 실DB 테스트가 맡는다.</p>
 */
class PortfolioEventServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

    private PortfolioTransactionMapper transactionMapper;
    private PortfolioEventProcessor processor;
    private PortfolioEventService service;

    @BeforeEach
    void setUp() {
        transactionMapper = mock(PortfolioTransactionMapper.class);
        processor = mock(PortfolioEventProcessor.class);
        service = new PortfolioEventService(transactionMapper, processor);
    }

    @Test
    @DisplayName("도래분을 건별로 처리하고 결과를 집계한다")
    void processesDueEventsOneByOne() {
        givenDue(event(1L, "interest-1"), event(2L, "interest-2"));
        when(processor.apply(any())).thenReturn(AssetEventOutcome.COMPLETED);

        PortfolioEventBatchResult result = service.process(NOW, 500);

        assertEquals(2, result.getProcessedCount());
        assertEquals(2, result.getCompletedCount());
        assertEquals(0, result.getFailedCount());
        verify(processor, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 그대로 처리된다 — 실패는 따로 기록한다")
    void isolatesFailuresFromTheRestOfTheBatch() {
        PortfolioTransaction broken = event(2L, "interest-broken");

        givenDue(event(1L, "interest-1"), broken, event(3L, "interest-3"));

        when(processor.apply(any())).thenReturn(AssetEventOutcome.COMPLETED);
        when(processor.apply(broken)).thenThrow(new IllegalStateException("보유하지 않은 상품"));

        PortfolioEventBatchResult result = service.process(NOW, 500);

        assertEquals(3, result.getProcessedCount());
        assertEquals(2, result.getCompletedCount(), "실패한 한 건이 나머지를 끌어내리면 안 됩니다.");
        assertEquals(1, result.getFailedCount());

        verify(processor).recordFailure(eq(broken), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("실패 기록에도 실패하면 로그만 남기고 배치를 계속한다")
    void keepsGoingWhenRecordingFailureAlsoFails() {
        PortfolioTransaction broken = event(1L, "interest-broken");

        givenDue(broken, event(2L, "interest-2"));

        when(processor.apply(any())).thenReturn(AssetEventOutcome.COMPLETED);
        when(processor.apply(broken)).thenThrow(new IllegalStateException("실패"));
        doThrow(new RuntimeException("DB 연결 끊김"))
                .when(processor).recordFailure(any(), any());

        PortfolioEventBatchResult result = service.process(NOW, 500);

        assertEquals(1, result.getCompletedCount());
        assertEquals(1, result.getFailedCount());
    }

    @Test
    @DisplayName("이미 처리된 이벤트는 실패가 아니라 건너뜀이다")
    void countsAlreadyProcessedEventsAsSkipped() {
        givenDue(event(1L, "interest-1"));
        when(processor.apply(any())).thenReturn(AssetEventOutcome.SKIPPED);

        PortfolioEventBatchResult result = service.process(NOW, 500);

        assertEquals(1, result.getProcessedCount());
        assertEquals(0, result.getCompletedCount());
        assertEquals(0, result.getFailedCount(), "중복 감지는 정상 동작이라 실패가 아닙니다.");
        assertEquals(1, result.getSkippedCount());
    }

    @Test
    @DisplayName("미래 시각으로는 처리하지 않는다 — 오지 않은 이자를 미리 주게 된다")
    void rejectsFutureProcessUntil() {
        ApiException exception = assertThrows(
                ApiException.class, () -> service.process(NOW.plusDays(1), 500));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(transactionMapper, never()).findDueScheduled(any(), anyInt());
    }

    @Test
    @DisplayName("건수를 주지 않으면 기본값을, 너무 크면 상한을 쓴다")
    void clampsBatchSize() {
        givenDue();

        service.process(NOW, null);
        verify(transactionMapper).findDueScheduled(NOW, PortfolioEventService.DEFAULT_BATCH_SIZE);

        service.process(NOW, 100000);
        verify(transactionMapper).findDueScheduled(NOW, PortfolioEventService.MAX_BATCH_SIZE);
    }

    // ------------------------------------------------------------------ 재처리

    @Test
    @DisplayName("실패한 이벤트를 다시 처리한다")
    void retriesFailedEvent() {
        PortfolioTransaction failed = event(1L, "interest-1");
        failed.setStatus(TransactionStatus.FAILED);

        PortfolioTransaction done = event(1L, "interest-1");
        done.setStatus(TransactionStatus.COMPLETED);
        done.setProcessedAt(NOW);

        when(transactionMapper.findByEventKey("interest-1")).thenReturn(failed, done);
        when(processor.apply(any())).thenReturn(AssetEventOutcome.COMPLETED);

        PortfolioEventResult result = service.retry("interest-1");

        verify(processor).apply(failed);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(NOW, result.getProcessedAt());
    }

    @Test
    @DisplayName("이미 완료된 이벤트는 추가 반영 없이 기존 결과를 돌려준다")
    void returnsExistingResultForCompletedEvent() {
        PortfolioTransaction done = event(1L, "interest-1");
        done.setStatus(TransactionStatus.COMPLETED);
        done.setProcessedAt(NOW);

        when(transactionMapper.findByEventKey("interest-1")).thenReturn(done);

        PortfolioEventResult result = service.retry("interest-1");

        assertEquals("COMPLETED", result.getStatus());
        verify(processor, never()).apply(any());
    }

    @Test
    @DisplayName("다시 실패하면 FAILED 상태를 그대로 돌려준다 — 요청 자체의 오류는 아니다")
    void returnsFailedStatusWhenRetryFailsAgain() {
        PortfolioTransaction failed = event(1L, "interest-1");
        failed.setStatus(TransactionStatus.FAILED);

        when(transactionMapper.findByEventKey("interest-1")).thenReturn(failed);
        when(processor.apply(any())).thenThrow(new IllegalStateException("여전히 실패"));

        PortfolioEventResult result = service.retry("interest-1");

        assertEquals("FAILED", result.getStatus());
        verify(processor).recordFailure(any(), any());
    }

    @Test
    @DisplayName("아직 오지 않았거나 취소된 이벤트는 재처리 대상이 아니다")
    void rejectsRetryOfNonFailedEvent() {
        PortfolioTransaction scheduled = event(1L, "interest-1");

        when(transactionMapper.findByEventKey(anyString())).thenReturn(scheduled);

        ApiException exception = assertThrows(
                ApiException.class, () -> service.retry("interest-1"));

        assertEquals(ErrorCode.EVENT_NOT_RETRYABLE, exception.getErrorCode());
        verify(processor, never()).apply(any());
    }

    @Test
    @DisplayName("없는 이벤트는 404다")
    void rejectsUnknownEvent() {
        when(transactionMapper.findByEventKey(anyString())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class, () -> service.retry("interest-없음"));

        assertEquals(ErrorCode.EVENT_NOT_FOUND, exception.getErrorCode());
    }

    // ------------------------------------------------------------------ 준비

    private void givenDue(PortfolioTransaction... events) {
        when(transactionMapper.findDueScheduled(any(), anyInt())).thenReturn(List.of(events));
    }

    private static PortfolioTransaction event(long id, String eventKey) {
        PortfolioTransaction event = new PortfolioTransaction();

        event.setPortfolioTransactionId(id);
        event.setPortfolioId(8001L);
        event.setHoldingId(9101L);
        event.setProductId(25L);
        event.setTransactionType(TransactionType.INTEREST);
        event.setAmount(new BigDecimal("10000.00"));
        event.setStatus(TransactionStatus.SCHEDULED);
        event.setScheduledAt(NOW.minusHours(1));
        event.setEventKey(eventKey);
        event.setIdempotencyKey(eventKey);

        return event;
    }
}
