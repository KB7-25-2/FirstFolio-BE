package org.firstfolio.simulation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.simulation.domain.ProductPrice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 장중 시세 캐시의 현재 상태 (점검용).
 *
 * <p>정상 폴링은 로그를 남기지 않아서(2초 주기라 하루 11,700줄이 된다) <b>캐시가 실제로
 * 채워지고 있는지 밖에서 볼 방법이 없었다.</b> 사용자 API로 확인하려면 Firebase 인증이 필요해
 * 점검 목적에는 쓰기 어렵다.</p>
 *
 * <p>{@code newest_reference_at}이 지금과 몇 초 이내로 가까우면 폴링이 살아 있다는 뜻이다.</p>
 */
@Schema(description = "장중 시세 캐시 상태")
public final class PriceCacheStatusResponse {

    @Schema(description = "캐시에 든 상품 수", example = "15")
    private final int cachedCount;

    @Schema(description = "지금 정규장이 열려 있는지", example = "true")
    private final boolean marketOpen;

    @Schema(description = "가장 오래된 기준 시각. 캐시가 비면 null")
    private final LocalDateTime oldestReferenceAt;

    @Schema(description = "가장 최근 기준 시각. 캐시가 비면 null")
    private final LocalDateTime newestReferenceAt;

    @Schema(description = "캐시에 든 항목")
    private final List<Item> items;

    private PriceCacheStatusResponse(
            int cachedCount,
            boolean marketOpen,
            LocalDateTime oldestReferenceAt,
            LocalDateTime newestReferenceAt,
            List<Item> items
    ) {
        this.cachedCount = cachedCount;
        this.marketOpen = marketOpen;
        this.oldestReferenceAt = oldestReferenceAt;
        this.newestReferenceAt = newestReferenceAt;
        this.items = items;
    }

    /** 캐시 스냅샷을 응답으로 옮긴다. 상품 id 순으로 정렬해 회차별 비교를 쉽게 한다. */
    public static PriceCacheStatusResponse of(List<ProductPrice> cached, boolean marketOpen) {
        List<Item> items = new ArrayList<>();
        LocalDateTime oldest = null;
        LocalDateTime newest = null;

        for (ProductPrice price : cached) {
            items.add(new Item(price.getProductId(), price.getPrice(), price.getReferenceAt()));

            LocalDateTime referenceAt = price.getReferenceAt();

            if (referenceAt == null) {
                continue;
            }

            if (oldest == null || referenceAt.isBefore(oldest)) {
                oldest = referenceAt;
            }

            if (newest == null || referenceAt.isAfter(newest)) {
                newest = referenceAt;
            }
        }

        items.sort(Comparator.comparing(Item::getProductId, Comparator.nullsLast(Long::compareTo)));

        return new PriceCacheStatusResponse(cached.size(), marketOpen, oldest, newest, items);
    }

    public int getCachedCount() {
        return cachedCount;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public LocalDateTime getOldestReferenceAt() {
        return oldestReferenceAt;
    }

    public LocalDateTime getNewestReferenceAt() {
        return newestReferenceAt;
    }

    public List<Item> getItems() {
        return items;
    }

    @Schema(description = "캐시된 한 상품")
    public static final class Item {

        @Schema(description = "상품 식별자", example = "87")
        private final Long productId;

        @Schema(description = "캐시된 가격", example = "231500.0000")
        private final BigDecimal price;

        @Schema(description = "이 값을 받은 폴링 시각")
        private final LocalDateTime referenceAt;

        private Item(Long productId, BigDecimal price, LocalDateTime referenceAt) {
            this.productId = productId;
            this.price = price;
            this.referenceAt = referenceAt;
        }

        public Long getProductId() {
            return productId;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public LocalDateTime getReferenceAt() {
            return referenceAt;
        }
    }
}
