package org.firstfolio.portfolio.dto.response;

import org.firstfolio.portfolio.service.PortfolioEventBatchResult;

/**
 * 자산 이벤트 처리 결과 (API_DOCS {@code POST /internal/portfolio-events/process}).
 *
 * <p>{@code processed = completed + failed + skipped}다. <b>건너뜀은 응답에 싣지 않는다</b> —
 * 명세에 없는 필드다. 대신 세 수가 맞지 않으면 그만큼이 건너뛴 건수이고, 자세한 내역은 로그에 있다.</p>
 */
public class PortfolioEventProcessResponse {

    private final int processedCount;
    private final int completedCount;
    private final int failedCount;

    /**
     * <b>언제나 null이다.</b>
     *
     * <p>도래분을 {@code batch_size}만큼 잘라 처리하고 남은 것은 다음 호출이 이어받는 구조라
     * 이어갈 커서가 없다. 명세에 있는 필드라 <b>생략하지 않고</b> null로 내보낸다.</p>
     */
    private final String nextCursor;

    public PortfolioEventProcessResponse(PortfolioEventBatchResult result) {
        this.processedCount = result.getProcessedCount();
        this.completedCount = result.getCompletedCount();
        this.failedCount = result.getFailedCount();
        this.nextCursor = null;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
