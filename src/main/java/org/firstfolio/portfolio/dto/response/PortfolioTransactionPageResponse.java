package org.firstfolio.portfolio.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래·자산 이벤트 이력 (API_DOCS {@code GET /portfolios/current/transactions}).
 *
 * <p>현재 세대의 이력만 담는다. 파산 신청으로 세대가 바뀌면 이전 이력은 여기 나오지 않는다
 * (감사 이력으로는 남는다, FUNC-037).</p>
 */
public class PortfolioTransactionPageResponse {

    private final List<Item> items;

    /** 다음 페이지가 없으면 null이다. */
    private final String nextCursor;

    public PortfolioTransactionPageResponse(List<Item> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    /**
     * 이력 한 건.
     *
     * <p>{@code SCHEDULED} 상태의 이자·배당·만기가 곧 "예정 이벤트"다 (FUNC-034 동작).
     * 이때는 {@code processedAt}이 비어 있고 {@code scheduledAt}에 예정 시각이 있다.</p>
     */
    public static final class Item {

        private final Long portfolioTransactionId;
        private final String transactionType;

        /** 대상 상품의 가명. 지급·초기화처럼 상품이 없는 이력에서는 null이다. */
        private final String displayName;

        private final BigDecimal amount;
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;
        private final String status;
        private final LocalDateTime scheduledAt;
        private final LocalDateTime processedAt;

        /** 수수료·세금·계산 근거. 저장된 JSON을 그대로 싣는다. */
        private final JsonNode detail;

        public Item(
                Long portfolioTransactionId,
                String transactionType,
                String displayName,
                BigDecimal amount,
                BigDecimal quantity,
                BigDecimal unitPrice,
                String status,
                LocalDateTime scheduledAt,
                LocalDateTime processedAt,
                JsonNode detail
        ) {
            this.portfolioTransactionId = portfolioTransactionId;
            this.transactionType = transactionType;
            this.displayName = displayName;
            this.amount = amount;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.status = status;
            this.scheduledAt = scheduledAt;
            this.processedAt = processedAt;
            this.detail = detail;
        }

        public Long getPortfolioTransactionId() {
            return portfolioTransactionId;
        }

        public String getTransactionType() {
            return transactionType;
        }

        public String getDisplayName() {
            return displayName;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getScheduledAt() {
            return scheduledAt;
        }

        public LocalDateTime getProcessedAt() {
            return processedAt;
        }

        public JsonNode getDetail() {
            return detail;
        }
    }

    public List<Item> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
