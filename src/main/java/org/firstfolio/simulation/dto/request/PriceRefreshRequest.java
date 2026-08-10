package org.firstfolio.simulation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 가격 갱신 요청 (API_DOCS {@code POST /internal/product-prices/refresh}).
 *
 * <p>{@code referenceAt}은 <b>이 갱신 실행의 기준 시점</b>이다. 저장되는 가격의
 * {@code reference_at}이 이 값이 되며, 외부 응답의 체결 시각과는 다르다
 * (체결 시각은 생성 키에 근거로 남는다).</p>
 */
@Schema(description = "공개 주식·펀드 기준 가격 갱신 요청")
public class PriceRefreshRequest {

    @Schema(description = "이번 갱신 실행의 기준 시각", example = "2026-08-07T09:00:00")
    private LocalDateTime referenceAt;

    /** 비우면 공개된 주식·펀드 전체를 갱신한다. */
    @Schema(description = "갱신할 상품 ID. 비우면 공개된 주식·펀드 전체", example = "[87, 88]")
    private List<Long> productIds;

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }

    public void setReferenceAt(LocalDateTime referenceAt) {
        this.referenceAt = referenceAt;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds;
    }
}
