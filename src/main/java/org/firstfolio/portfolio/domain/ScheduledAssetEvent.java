package org.firstfolio.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 아직 오지 않은 자산 이벤트 한 건 (FUNC-041).
 *
 * <p><b>금액이 이미 확정돼 있다.</b> 가입·매수 시점에 전 일정을 만들면서 함께 계산한다.
 * 원금과 이율이 그 시점에 고정되므로 나중에 다시 계산할 이유가 없고, 두 번 계산하면
 * 두 값이 어긋날 자리가 생긴다. 배치는 이 금액을 현금에 반영하기만 한다.</p>
 *
 * <p>{@code periodMonths}·{@code ratePercent}는 계산 근거다. 사용자에게 "왜 이 금액인가"를
 * 설명할 수 있어야 하고, 이자 정책이 확정되면 기존 예정분을 검산할 수 있어야 한다.</p>
 */
public final class ScheduledAssetEvent {

    private final TransactionType type;
    private final LocalDateTime scheduledAt;
    private final BigDecimal amount;
    private final AssetEventBasis basis;
    private final int periodMonths;
    private final BigDecimal ratePercent;

    public ScheduledAssetEvent(
            TransactionType type,
            LocalDateTime scheduledAt,
            BigDecimal amount,
            AssetEventBasis basis,
            int periodMonths,
            BigDecimal ratePercent
    ) {
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.amount = amount;
        this.basis = basis;
        this.periodMonths = periodMonths;
        this.ratePercent = ratePercent;
    }

    /** {@code INTEREST} 또는 {@code MATURITY}. */
    public TransactionType getType() {
        return type;
    }

    /** 서비스 내 시각(UTC). 압축된 기간이 적용된 값이다. */
    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    /** 현금이 이만큼 는다. 이자든 원금이든 부호는 항상 양수다. */
    public BigDecimal getAmount() {
        return amount;
    }

    public AssetEventBasis getBasis() {
        return basis;
    }

    /**
     * 이 지급이 대응하는 <b>실제</b> 기간(개월). 서비스 내 압축 기간이 아니다.
     *
     * <p>이표채의 마지막 회차는 주기보다 짧을 수 있다 — 32개월 만기에 12개월 주기면
     * 12·12개월을 받고 만기에 8개월치를 받는다. 원금 반환은 기간 개념이 없어 0이다.</p>
     */
    public int getPeriodMonths() {
        return periodMonths;
    }

    /** 적용한 연이율·쿠폰금리(%). 원금 반환에는 없다(null). */
    public BigDecimal getRatePercent() {
        return ratePercent;
    }
}
