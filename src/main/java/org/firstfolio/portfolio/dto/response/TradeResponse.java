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
 *
 * <h3>금액이 세 개인 이유</h3>
 *
 * <table border="1">
 *   <caption>500만원으로 241,500원짜리를 살 때</caption>
 *   <tr><th>필드</th><th>값</th><th>뜻</th></tr>
 *   <tr><td>{@code requestedAmount}</td><td>5,000,000</td><td>사용자가 넣겠다고 한 돈</td></tr>
 *   <tr><td>{@code amount}</td><td>4,830,000</td><td>실제 체결된 20주어치</td></tr>
 *   <tr><td>{@code feeAmount}</td><td>724.50</td><td>수수료 0.015%</td></tr>
 *   <tr><td>{@code taxAmount}</td><td>0.00</td><td>증권거래세 — <b>매도에만</b> 붙는다</td></tr>
 *   <tr><td>{@code netCashAmount}</td><td>4,830,724.50</td><td><b>현금에서 실제로 빠진 돈</b></td></tr>
 * </table>
 *
 * <p><b>화면이 "얼마 나갔는지"로 보여줄 값은 {@code netCashAmount}</b>다. 체결액만 보여주면
 * 잔액이 맞지 않는 것으로 보인다. 비용이 붙지 않는 거래에서도 {@code feeAmount}·{@code taxAmount}는
 * {@code null}이 아니라 <b>{@code 0.00}</b>이다.</p>
 *
 * <p>주식·펀드를 팔면 <b>증권거래세가 수수료보다 13배 크다</b>(0.20% 대 0.015%). 사용자가 체감하는
 * 비용은 이쪽이므로 매도 화면에서 빠뜨리지 않아야 한다.</p>
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
    @Schema(description = "매매 수수료. 예·적금·채권 거래는 0.00", type = "string", example = "289.80")
    private final BigDecimal feeAmount;
    @Schema(
            description = "증권거래세. 매도에만 붙으며 매수·예·적금·채권 거래는 0.00",
            type = "string",
            example = "3864.00"
    )
    private final BigDecimal taxAmount;
    @Schema(
            description = "현금에서 실제로 빠지거나 들어온 금액. 매수는 체결액보다 크고 매도는 작다",
            type = "string",
            example = "1931710.20"
    )
    private final BigDecimal netCashAmount;
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
        this.feeAmount = result.getFeeAmount();
        this.taxAmount = result.getTaxAmount();
        this.netCashAmount = result.getNetCashAmount();
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

    /** 매매 수수료. 예·적금·채권 거래는 {@code 0.00}. */
    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    /** 증권거래세. 매도에만 붙으며 그 밖에는 {@code 0.00}. */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /** 현금에서 실제로 오간 금액. 화면이 "얼마 나갔는지"로 보여줄 값이다. */
    public BigDecimal getNetCashAmount() {
        return netCashAmount;
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
