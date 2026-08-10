package org.firstfolio.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 커서 페이지네이션 응답. {@code nextCursor}가 null이면 마지막 페이지다.
 */
@Schema(description = "관리자용 모의 상품 커서 페이지")
public class AdminProductPageResponse {

    @Schema(description = "관리자용 상품 목록")
    private final List<AdminProductResponse> items;
    @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "product-25")
    private final String nextCursor;

    public AdminProductPageResponse(List<AdminProductResponse> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public List<AdminProductResponse> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
