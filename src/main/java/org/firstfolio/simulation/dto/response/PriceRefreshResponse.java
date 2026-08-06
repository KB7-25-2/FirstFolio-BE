package org.firstfolio.simulation.dto.response;

import org.firstfolio.simulation.service.PriceRefreshResult;

import java.time.LocalDateTime;

/**
 * 가격 갱신 결과 (API_DOCS {@code POST /internal/product-prices/refresh}).
 *
 * <p>세 수가 맞아떨어지는지가 곧 진단이다 — {@code processed = created + skipped}가 아니면
 * 어딘가에서 상품이 조용히 빠졌다는 뜻이다. 배치 로그에서 바로 확인할 수 있게 셋 다 내보낸다.</p>
 */
public class PriceRefreshResponse {

    private final LocalDateTime referenceAt;
    private final int processedCount;
    private final int createdCount;
    private final int skippedCount;

    public PriceRefreshResponse(PriceRefreshResult result) {
        this.referenceAt = result.getReferenceAt();
        this.processedCount = result.getProcessedCount();
        this.createdCount = result.getCreatedCount();
        this.skippedCount = result.getSkippedCount();
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
