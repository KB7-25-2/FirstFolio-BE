package org.firstfolio.portfolio.service;

/**
 * 배치 한 번의 처리 결과 (FUNC-041).
 *
 * <p>{@code processedCount = completedCount + failedCount + skippedCount}다.
 * 건너뛴 건수를 따로 두는 이유는 <b>실패가 아니기 때문</b>이다 ({@link AssetEventOutcome} 참고).
 * API 응답에는 명세대로 처리·완료·실패만 싣고, 건너뜀은 로그로 남긴다.</p>
 */
public final class PortfolioEventBatchResult {

    private final int processedCount;
    private final int completedCount;
    private final int failedCount;
    private final int skippedCount;

    public PortfolioEventBatchResult(
            int processedCount,
            int completedCount,
            int failedCount,
            int skippedCount
    ) {
        this.processedCount = processedCount;
        this.completedCount = completedCount;
        this.failedCount = failedCount;
        this.skippedCount = skippedCount;
    }

    /** 도래분으로 읽어 시도한 건수. */
    public int getProcessedCount() {
        return processedCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
}
