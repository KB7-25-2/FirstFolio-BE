package org.firstfolio.simulation.service;

import java.time.LocalDateTime;

/**
 * 가격 갱신 한 번의 결과 (FUNC-040).
 *
 * <p>세 수가 맞아떨어지는지가 곧 진단이다 — {@code processed = created + skipped}가 아니면
 * 어딘가에서 조용히 사라진 상품이 있다는 뜻이다.</p>
 */
public final class PriceRefreshResult {

    private final LocalDateTime referenceAt;

    /** 갱신 대상으로 고른 상품 수. */
    private final int processedCount;

    /** 새로 저장한 가격 행 수. */
    private final int createdCount;

    /** 이미 있거나(멱등) 값을 얻지 못해 건너뛴 수. */
    private final int skippedCount;

    public PriceRefreshResult(
            LocalDateTime referenceAt,
            int processedCount,
            int createdCount,
            int skippedCount
    ) {
        this.referenceAt = referenceAt;
        this.processedCount = processedCount;
        this.createdCount = createdCount;
        this.skippedCount = skippedCount;
    }

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
}
