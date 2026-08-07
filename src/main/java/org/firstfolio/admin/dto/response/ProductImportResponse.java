package org.firstfolio.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.simulation.service.ProductImportResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 가져오기 응답 (API_DOCS {@code POST /admin/financial-products/imports}).
 *
 * <p>{@code skippedCount}는 문서 예시에 없는 추가 필드다. 재수집 시 이미 등록된 상품은
 * 건너뛰는데, {@code imported_count: 0}만 돌려주면 실패인지 중복인지 구분할 수 없다.</p>
 */
@Schema(description = "외부 원천 상품 수집 결과")
public class ProductImportResponse {

    @Schema(description = "새로 등록한 상품 수", example = "12")
    private final int importedCount;
    @Schema(description = "이미 등록되어 건너뛴 상품 수", example = "3")
    private final int skippedCount;
    @Schema(description = "새로 등록한 모의 상품 ID")
    private final List<Long> productIds;
    @Schema(description = "원천 데이터 기준 시각", example = "2026-08-07T09:00:00")
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
