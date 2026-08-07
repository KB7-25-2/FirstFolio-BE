package org.firstfolio.portfolio.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래·자산 이벤트 이력 (API_DOCS {@code GET /portfolios/current/transactions}).
 *
 * <p>현재 세대의 이력만 담는다. 파산 신청으로 세대가 바뀌면 이전 이력은 여기 나오지 않는다
 * (감사 이력으로는 남는다, FUNC-037).</p>
 */
@Schema(description = "현재 포트폴리오 세대의 거래·자산 이벤트 이력")
public class PortfolioTransactionPageResponse {

    @Schema(description = "거래·자산 이벤트 목록")
    private final List<Item> items;

    /** 다음 페이지가 없으면 null이다. */
    @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "txn-8201")
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
    @Schema(description = "거래 또는 예정·처리된 자산 이벤트 한 건")
    public static final class Item {

        @Schema(description = "포트폴리오 거래 ID", example = "8201")
        private final Long portfolioTransactionId;
        @Schema(description = "거래·이벤트 유형", example = "INTEREST")
        private final String transactionType;

        /** 대상 상품의 가명. 지급·초기화처럼 상품이 없는 이력에서는 null이다. */
        @Schema(description = "대상 상품 가명. 지급·초기화처럼 상품이 없으면 null", example = "푸른나무 정기예금")
        private final String displayName;

        @Schema(description = "거래 또는 이벤트 금액", type = "string", example = "12000.00")
        private final BigDecimal amount;
        @Schema(description = "매수·매도 수량. 그 밖의 유형은 null", type = "string", example = "8.000000")
        private final BigDecimal quantity;
        @Schema(description = "매수·매도 단가. 그 밖의 유형은 null", type = "string", example = "241500.0000")
        private final BigDecimal unitPrice;
        @Schema(description = "처리 상태", example = "COMPLETED")
        private final String status;
        @Schema(description = "예정 이벤트 도래 시각. 즉시 거래는 null", example = "2026-08-07T04:10:00")
        private final LocalDateTime scheduledAt;
        @Schema(description = "실제 반영 시각. 예정 또는 실패 상태면 null", example = "2026-08-07T04:10:01")
        private final LocalDateTime processedAt;

        /** 수수료·세금·계산 근거. 저장된 JSON을 그대로 싣는다. */
        @Schema(description = "수수료·세금·계산 근거 등 추적용 상세 JSON")
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
