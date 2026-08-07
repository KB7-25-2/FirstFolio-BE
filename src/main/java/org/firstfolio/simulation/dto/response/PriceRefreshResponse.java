package org.firstfolio.simulation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.simulation.service.PriceRefreshResult;

import java.time.LocalDateTime;

/**
 * 가격 갱신 결과 (API_DOCS {@code POST /internal/product-prices/refresh}).
 *
 * <p>세 수가 맞아떨어지는지가 곧 진단이다 — {@code processed = created + skipped}가 아니면
 * 어딘가에서 상품이 조용히 빠졌다는 뜻이다. 배치 로그에서 바로 확인할 수 있게 셋 다 내보낸다.</p>
 */
@Schema(description = "상품 기준 가격 갱신 결과")
public class PriceRefreshResponse {

    @Schema(description = "갱신 기준 시각", example = "2026-08-07T09:00:00")
    private final LocalDateTime referenceAt;
    @Schema(description = "처리 대상 상품 수", example = "10")
    private final int processedCount;
    @Schema(description = "새 가격 이력 생성 수", example = "9")
    private final int createdCount;
    @Schema(description = "중복 등으로 건너뛴 수", example = "1")
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
