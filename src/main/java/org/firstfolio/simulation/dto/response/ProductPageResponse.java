package org.firstfolio.simulation.dto.response;

import java.util.List;

/**
 * 사용자용 상품 목록 응답. {@code nextCursor}가 null이면 마지막 페이지다.
 */
public class ProductPageResponse {

    private final List<ProductSummaryResponse> items;
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
