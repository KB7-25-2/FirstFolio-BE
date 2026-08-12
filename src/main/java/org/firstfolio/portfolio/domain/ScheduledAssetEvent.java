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
 *
 * <h3>이자소득세는 여기서 이미 떼어져 있다</h3>
 *
 * <p>{@code amount}는 <b>세후</b>다. 배치({@code PortfolioEventProcessor})가 이 값을 그대로
 * 현금에 더하므로, 세금을 떼는 자리는 여기 한 곳이어야 한다. 반영 시점에 세율을 다시 곱하면
 * <b>완료 표시 먼저, 반영은 그다음</b>이라는 실패 격리 순서가 깨진다.</p>
 *
 * <p>{@code grossAmount}(세전)와 {@code taxAmount}는 화면과 검산을 위해 함께 들고 다닌다 —
 * 사용자는 "이자 10만원 중 15,400원을 세금으로 뗐다"를 볼 수 있어야 한다.
 * 원금 반환({@code MATURITY})은 소득이 아니므로 세금이 0이고 세전과 세후가 같다.</p>
 */
public final class ScheduledAssetEvent {

    private final TransactionType type;
    private final LocalDateTime scheduledAt;
    private final BigDecimal amount;
    private final BigDecimal grossAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal taxRate;
    private final AssetEventBasis basis;
    private final int periodMonths;
    private final BigDecimal ratePercent;

    public ScheduledAssetEvent(
            TransactionType type,
            LocalDateTime scheduledAt,
            BigDecimal amount,
            BigDecimal grossAmount,
            BigDecimal taxAmount,
            BigDecimal taxRate,
            AssetEventBasis basis,
            int periodMonths,
            BigDecimal ratePercent
    ) {
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.amount = amount;
        this.grossAmount = grossAmount;
        this.taxAmount = taxAmount;
        this.taxRate = taxRate;
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

    /**
     * 현금이 이만큼 는다. 이자든 원금이든 부호는 항상 양수다.
     *
     * <p><b>이자소득세를 뗀 뒤의 금액</b>이다. 세전 금액은 {@link #getGrossAmount()}.</p>
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /** 세금을 떼기 전 금액. 원금 반환에서는 {@link #getAmount()}와 같다. */
    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    /** 원천징수한 이자소득세. 원금 반환은 소득이 아니므로 {@code 0.00}이다. */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /** 이 지급에 실제로 적용된 이자소득세율. 0.154 = 15.4% */
    public BigDecimal getTaxRate() {
        return taxRate;
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
