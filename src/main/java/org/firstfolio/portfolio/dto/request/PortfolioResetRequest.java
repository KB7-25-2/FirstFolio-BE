package org.firstfolio.portfolio.dto.request;

/**
 * 포트폴리오 초기화 요청 (API_DOCS {@code POST /portfolios/current/reset}).
 *
 * <p>{@code confirmation}은 고정 문구 {@code RESET_PORTFOLIO}여야 한다. 되돌릴 수 없는 동작이라
 * 실수로 호출되는 것을 막는 장치다 — 화면의 확인 절차와 별개로 서버도 확인한다.</p>
 */
public class PortfolioResetRequest {

    private String confirmation;
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
