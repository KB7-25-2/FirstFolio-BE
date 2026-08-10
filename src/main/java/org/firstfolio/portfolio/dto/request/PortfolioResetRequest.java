package org.firstfolio.portfolio.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 포트폴리오 초기화 요청 (API_DOCS {@code POST /portfolios/current/reset}).
 *
 * <p>{@code confirmation}은 고정 문구 {@code RESET_PORTFOLIO}여야 한다. 되돌릴 수 없는 동작이라
 * 실수로 호출되는 것을 막는 장치다 — 화면의 확인 절차와 별개로 서버도 확인한다.</p>
 */
@Schema(description = "포트폴리오 초기화 확인 정보")
public class PortfolioResetRequest {

    @Schema(description = "초기화 고정 확인 문구", example = "RESET_PORTFOLIO", allowableValues = "RESET_PORTFOLIO")
    private String confirmation;
    @Schema(description = "중복 초기화 방지 키", example = "reset-101-2")
    private String idempotencyKey;

    public String getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(String confirmation) {
        this.confirmation = confirmation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
