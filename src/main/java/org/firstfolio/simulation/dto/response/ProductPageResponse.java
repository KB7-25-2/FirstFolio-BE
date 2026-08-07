package org.firstfolio.simulation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 사용자용 상품 목록 응답. {@code nextCursor}가 null이면 마지막 페이지다.
 */
@Schema(description = "사용자용 가명 모의 상품 커서 페이지")
public class ProductPageResponse {

    @Schema(description = "공개 모의 상품 목록")
    private final List<ProductSummaryResponse> items;
    @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "product-25")
    private final String nextCursor;

    public ProductPageResponse(List<ProductSummaryResponse> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public List<ProductSummaryResponse> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
