package org.firstfolio.news.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * FE {@code FinancialNewsListResponse} 타입과 맞춘 {@code { items: [...] } }형태.
 */
@Schema(description = "금융 뉴스 목록 응답")
public final class FinancialNewsListResponse {

    @Schema(description = "금융 뉴스 목록")
    private final List<FinancialNewsItemResponse> items;

    public FinancialNewsListResponse(List<FinancialNewsItemResponse> items) {
        this.items = items;
    }

    public List<FinancialNewsItemResponse> getItems() {
        return items;
    }
}
