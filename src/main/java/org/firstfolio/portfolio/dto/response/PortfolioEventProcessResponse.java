package org.firstfolio.portfolio.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.portfolio.service.PortfolioEventBatchResult;

/**
 * 자산 이벤트 처리 결과 (API_DOCS {@code POST /internal/portfolio-events/process}).
 *
 * <p>{@code processed = completed + failed + skipped}다. <b>건너뜀은 응답에 싣지 않는다</b> —
 * 명세에 없는 필드다. 대신 세 수가 맞지 않으면 그만큼이 건너뛴 건수이고, 자세한 내역은 로그에 있다.</p>
 */
@Schema(description = "도래한 자산 이벤트 일괄 처리 결과")
public class PortfolioEventProcessResponse {

    @Schema(description = "이번 호출에서 확인한 이벤트 수", example = "3")
    private final int processedCount;
    @Schema(description = "처리 완료 수", example = "2")
    private final int completedCount;
    @Schema(description = "처리 실패 수", example = "1")
    private final int failedCount;

    /**
     * <b>언제나 null이다.</b>
     *
     * <p>도래분을 {@code batch_size}만큼 잘라 처리하고 남은 것은 다음 호출이 이어받는 구조라
     * 이어갈 커서가 없다. 명세에 있는 필드라 <b>생략하지 않고</b> null로 내보낸다.</p>
     */
    @Schema(description = "현재 구현에서는 항상 null. 남은 이벤트는 다음 호출이 이어서 처리", example = "null")
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
