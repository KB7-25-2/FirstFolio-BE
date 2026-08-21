package org.firstfolio.simulation.service;

/** 캔들 동기화 한 회차의 처리 결과. */
public final class CandleSyncResult {

    private final int processedProductCount;
    private final int savedCandleCount;
    private final int failedProductCount;

    public CandleSyncResult(int processedProductCount, int savedCandleCount, int failedProductCount) {
        this.processedProductCount = processedProductCount;
        this.savedCandleCount = savedCandleCount;
        this.failedProductCount = failedProductCount;
    }

    public int getProcessedProductCount() {
        return processedProductCount;
    }

    public int getSavedCandleCount() {
        return savedCandleCount;
    }

    public int getFailedProductCount() {
        return failedProductCount;
    }

    public boolean isSuccessful() {
        return failedProductCount == 0;
    }
}
