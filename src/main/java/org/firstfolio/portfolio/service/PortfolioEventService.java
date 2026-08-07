package org.firstfolio.portfolio.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 도래한 자산 이벤트를 반영하는 내부 배치 (FUNC-041).
 *
 * <h3>이 클래스에는 트랜잭션이 없다</h3>
 *
 * <p><b>일부러 없다.</b> 여기에 {@code @Transactional}을 붙이면 배치 전체가 트랜잭션 하나가 되어
 * 마지막 한 건이 실패할 때 앞의 수백 건이 함께 롤백된다. 명세가 금지하는 바로 그 동작이다
 * (<i>"개별 실패가 전체 배치의 성공 건을 롤백하지 않게 격리한다"</i>).
 * 트랜잭션 경계는 {@link PortfolioEventProcessor}가 건별로 갖는다.</p>
 *
 * <h3>이자가 두 번 들어오지 않는 이유</h3>
 *
 * <p>같은 배치를 두 번 돌려도 {@code markCompleted}가 {@code WHERE status IN ('SCHEDULED','FAILED')}
 * 조건을 갖고 있어 두 번째에는 갱신 행이 0이 되고, 현금에 손도 대지 않고 건너뛴다.
 * 스케줄러 재시도·수동 재실행·재처리 요청이 겹쳐도 마찬가지다.</p>
 */
@Service
public class PortfolioEventService {

    /** 명세 예시가 500이다. 요청이 크기를 주지 않으면 이 값을 쓴다. */
    static final int DEFAULT_BATCH_SIZE = 500;

    /** 한 번에 메모리로 읽는 상한. 더 큰 값이 와도 여기서 끊고 다음 호출로 넘긴다. */
    static final int MAX_BATCH_SIZE = 1000;

    private static final Logger log = LogManager.getLogger(PortfolioEventService.class);

    private final PortfolioTransactionMapper transactionMapper;
    private final PortfolioEventProcessor processor;

    public PortfolioEventService(
            PortfolioTransactionMapper transactionMapper,
            PortfolioEventProcessor processor
    ) {
        this.transactionMapper = transactionMapper;
        this.processor = processor;
    }

    /**
     * {@code processUntil}까지 도래한 예정 이벤트를 처리한다.
     *
     * <p>한 번에 {@code batchSize}건까지만 본다. 남은 것은 다음 호출에서 이어서 처리되므로
     * 커서를 돌려주지 않는다.</p>
     *
     * @param processUntil 이 시각까지 도래한 이벤트. <b>미래 시각은 거부한다</b>
     * @param batchSize    null이면 {@link #DEFAULT_BATCH_SIZE}
     */
    public PortfolioEventBatchResult process(LocalDateTime processUntil, Integer batchSize) {
        requireValidProcessUntil(processUntil);

        List<PortfolioTransaction> due =
                transactionMapper.findDueScheduled(processUntil, limitOf(batchSize));

        int completed = 0;
        int failed = 0;
        int skipped = 0;

        for (PortfolioTransaction event : due) {
            AssetEventOutcome outcome = handle(event);

            if (outcome == null) {
                failed++;
            } else if (outcome == AssetEventOutcome.COMPLETED) {
                completed++;
            } else {
                skipped++;
            }
        }

        log.info(
                "자산 이벤트 배치 완료 기준={} 처리={} 완료={} 실패={} 건너뜀={}",
                processUntil, due.size(), completed, failed, skipped
        );

        return new PortfolioEventBatchResult(due.size(), completed, failed, skipped);
    }

    /**
     * 실패한 이벤트를 같은 키로 다시 처리한다.
     *
     * <ul>
     *   <li>{@code FAILED} — 다시 시도한다. 또 실패하면 상태가 {@code FAILED}인 채로 돌려준다.</li>
     *   <li>{@code COMPLETED} — <b>추가 반영 없이</b> 기존 결과를 그대로 돌려준다.</li>
     *   <li>{@code SCHEDULED}·{@code CANCELLED} — 재처리 대상이 아니다 (409).</li>
     * </ul>
     */
    public PortfolioEventResult retry(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이벤트 키가 필요합니다.");
        }

        PortfolioTransaction event = transactionMapper.findByEventKey(eventKey);

        if (event == null) {
            throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
        }

        if (event.getStatus() == TransactionStatus.COMPLETED) {
            log.info("이미 완료된 이벤트입니다 eventKey={}", eventKey);

            return toResult(event);
        }

        if (event.getStatus() != TransactionStatus.FAILED) {
            throw new ApiException(
                    ErrorCode.EVENT_NOT_RETRYABLE,
                    "실패한 이벤트만 재처리할 수 있습니다. 현재 상태=" + event.getStatus()
            );
        }

        handle(event);

        // 처리 결과는 DB가 갖고 있다. 다시 읽어 실제 상태를 돌려준다.
        PortfolioTransaction after = transactionMapper.findByEventKey(eventKey);

        return toResult(after == null ? event : after);
    }

    /**
     * 한 건을 처리하고, 실패하면 별도 트랜잭션으로 실패를 남긴다.
     *
     * <p>실패 기록마저 실패해도 <b>배치는 계속 간다.</b> 한 건 때문에 나머지 도래분이 통째로
     * 밀리는 것이 더 나쁘다.</p>
     *
     * @return 실패했으면 null
     */
    private AssetEventOutcome handle(PortfolioTransaction event) {
        try {
            return processor.apply(event);
        } catch (Exception exception) {
            log.warn("자산 이벤트 처리 실패 eventKey={}", event.getEventKey(), exception);

            try {
                processor.recordFailure(event, exception);
            } catch (Exception recordFailure) {
                log.error("실패 기록에도 실패했습니다 eventKey={}", event.getEventKey(), recordFailure);
            }

            return null;
        }
    }

    private static PortfolioEventResult toResult(PortfolioTransaction event) {
        return new PortfolioEventResult(
                event.getEventKey(),
                event.getStatus() == null ? null : event.getStatus().name(),
                event.getPortfolioTransactionId(),
                event.getProcessedAt()
        );
    }

    private static int limitOf(Integer batchSize) {
        if (batchSize == null || batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }

        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    /**
     * 미래 시각으로는 처리하지 않는다.
     *
     * <p>압축된 시간이 아직 도달하지 않은 이자를 <b>미리 지급</b>하게 된다. 시간 압축으로
     * 만기를 기다리게 만든 의미가 사라진다. 가격 갱신이 미래 기준 시점을 거부하는 것과 같은 이유다
     * (FUNC-040).</p>
     */
    private static void requireValidProcessUntil(LocalDateTime processUntil) {
        if (processUntil == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "처리 기준 시각이 필요합니다.");
        }

        if (processUntil.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "미래 시점의 이벤트는 처리할 수 없습니다."
            );
        }
    }
}
