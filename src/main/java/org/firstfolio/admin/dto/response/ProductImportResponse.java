package org.firstfolio.admin.dto.response;

import org.firstfolio.simulation.service.ProductImportResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 가져오기 응답 (API_DOCS {@code POST /admin/financial-products/imports}).
 *
 * <p>{@code skippedCount}는 문서 예시에 없는 추가 필드다. 재수집 시 이미 등록된 상품은
 * 건너뛰는데, {@code imported_count: 0}만 돌려주면 실패인지 중복인지 구분할 수 없다.</p>
 */
public class ProductImportResponse {

    private final int importedCount;
    private final int skippedCount;
    private final List<Long> productIds;
    private final LocalDateTime referenceAt;

    public ProductImportResponse(ProductImportResult result) {
        this.importedCount = result.getImportedCount();
        this.skippedCount = result.getSkippedCount();
        this.productIds = result.getProductIds();
        this.referenceAt = result.getReferenceAt();
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
