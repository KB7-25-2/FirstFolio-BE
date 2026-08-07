package org.firstfolio.portfolio.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.portfolio.service.PortfolioResetResult;

import java.math.BigDecimal;

/**
 * 포트폴리오 초기화 결과 (API_DOCS {@code POST /portfolios/current/reset}).
 *
 * <p>닫힌 세대와 새 세대를 함께 준다. 화면이 "무엇이 끝나고 무엇이 시작됐는지"를 알아야
 * 전환과 안내를 만들 수 있다.</p>
 */
@Schema(description = "포트폴리오 초기화와 새 세대 생성 결과")
public class PortfolioResetResponse {

    @Schema(description = "종료된 포트폴리오 ID", example = "8001")
    private final Long closedPortfolioId;
    @Schema(description = "새 활성 포트폴리오 ID", example = "8002")
    private final Long newPortfolioId;
    @Schema(description = "새 포트폴리오 세대 번호", example = "2")
    private final Integer generationNo;
    @Schema(description = "새로 지급된 모의 현금", type = "string", example = "30000000.00")
    private final BigDecimal cashBalance;
    @Schema(description = "초기화 이력 거래 ID", example = "8299")
    private final Long resetTransactionId;

    public PortfolioResetResponse(PortfolioResetResult result) {
        this.closedPortfolioId = result.getClosedPortfolioId();
        this.newPortfolioId = result.getNewPortfolioId();
        this.generationNo = result.getGenerationNo();
        this.cashBalance = result.getCashBalance();
        this.resetTransactionId = result.getResetTransactionId();
    }

    public Long getClosedPortfolioId() {
        return closedPortfolioId;
    }

    public Long getNewPortfolioId() {
        return newPortfolioId;
    }

    public Integer getGenerationNo() {
        return generationNo;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public Long getResetTransactionId() {
        return resetTransactionId;
    }
}
