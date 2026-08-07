package org.firstfolio.portfolio.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.portfolio.service.TradeResult;

import java.math.BigDecimal;

/**
 * 체결 결과 (API_DOCS {@code POST /portfolios/current/trades}).
 *
 * <p>{@code quantity}·{@code unitPrice}는 <b>예·적금·채권 거래에서 null</b>이다 — 수량과 단가
 * 개념이 없다.</p>
 *
 * <p>{@code requestedAmount}와 {@code amount}가 다를 수 있는 경우는 <b>주식·펀드 매수 한 곳뿐</b>이다.
 * 정수 주수로 내림하기 때문이다(500만원 요청 → 483만원 체결). 화면은 이 차이를 사용자에게 알려야
 * 한다 — 안 알리면 돈이 사라진 것으로 보인다.</p>
 */
@Schema(description = "모의 상품 거래 체결 결과")
public class TradeResponse {

    @Schema(description = "포트폴리오 거래 ID", example = "8201")
    private final Long portfolioTransactionId;
    @Schema(description = "거래 유형", example = "SELL")
    private final String transactionType;
    @Schema(description = "모의 상품 ID", example = "87")
    private final Long productId;
    @Schema(description = "클라이언트가 요청한 금액", type = "string", example = "1932000.00")
    private final BigDecimal requestedAmount;
    @Schema(description = "서버가 확정한 실제 체결 금액", type = "string", example = "1932000.00")
    private final BigDecimal amount;
    @Schema(description = "체결 수량. 가입형 상품은 null", type = "string", example = "8.000000")
    private final BigDecimal quantity;
    @Schema(description = "체결 단가. 가입형 상품은 null", type = "string", example = "241500.0000")
    private final BigDecimal unitPrice;
    @Schema(description = "거래 상태", example = "COMPLETED")
    private final String status;
    @Schema(description = "체결 후 모의 현금", type = "string", example = "7932000.00")
    private final BigDecimal cashBalance;

    public TradeResponse(TradeResult result) {
        this.portfolioTransactionId = result.getPortfolioTransactionId();
        this.transactionType = result.getTransactionType();
        this.productId = result.getProductId();
        this.requestedAmount = result.getRequestedAmount();
        this.amount = result.getAmount();
        this.quantity = result.getQuantity();
        this.unitPrice = result.getUnitPrice();
        this.status = result.getStatus();
        this.cashBalance = result.getCashBalance();
    }

    public Long getPortfolioTransactionId() {
        return portfolioTransactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
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

    /** 체결 후 남은 모의 현금. */
    public BigDecimal getCashBalance() {
        return cashBalance;
    }
}
