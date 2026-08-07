package org.firstfolio.admin.dto.response;

import java.util.List;

/**
 * 커서 페이지네이션 응답. {@code nextCursor}가 null이면 마지막 페이지다.
 */
public class AdminProductPageResponse {

    private final List<AdminProductResponse> items;
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
