package org.firstfolio.simulation.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 가져오기 결과.
 *
 * <p>{@code skippedCount}는 API_DOCS 예시에 없는 추가 필드다. 재수집 시 이미 등록된 상품은
 * 건너뛰는데, 그때 {@code imported_count: 0}만 돌려주면 실패인지 중복인지 구분할 수 없다.
 * 관리자 전용 응답이라 사용자 API 계약에는 영향이 없다.</p>
 */
public class ProductImportResult {

    private final int importedCount;
    private final int skippedCount;
    private final List<Long> productIds;
    private final LocalDateTime referenceAt;

    public ProductImportResult(
            int skippedCount,
            List<Long> productIds,
            LocalDateTime referenceAt
    ) {
        this.importedCount = productIds.size();
        this.skippedCount = skippedCount;
        this.productIds = List.copyOf(productIds);
        this.referenceAt = referenceAt;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }
}
