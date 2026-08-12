package org.firstfolio.quiz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.portfolio.service.InitialGrantResult;

import java.math.BigDecimal;

@Schema(description = "포트폴리오 기초 과정 최초 완료 지급 결과")
public record FoundationGrantResponse(
        @Schema(description = "초기 모의투자금 지급 완료 여부", example = "true") boolean granted,
        @Schema(description = "초기 모의투자금", example = "30000000.00") BigDecimal amount,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "생성된 포트폴리오 ID", example = "501", nullable = true) Long portfolioId
) {
    public static FoundationGrantResponse from(InitialGrantResult result) {
        return new FoundationGrantResponse(
                result.getPortfolioId() != null,
                result.getAmount(),
                result.getPortfolioId()
        );
    }
}
