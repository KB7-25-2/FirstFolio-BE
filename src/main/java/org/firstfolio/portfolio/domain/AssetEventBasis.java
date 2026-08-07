package org.firstfolio.portfolio.domain;

/**
 * 예정 이벤트 금액이 어떤 근거로 나왔는지 (FUNC-041).
 *
 * <p>{@code detail_json}에 그대로 남긴다. <b>이자 계산식은 확정된 정책이 없어</b>
 * (FSD는 "별도 정책으로 관리"라고만 하고 SIMULATION_POLICY_v3에도 없다) 우리가 정한 가정치인데,
 * 어떤 식으로 계산했는지를 데이터에 남겨 두지 않으면 나중에 금액만 보고는 확인할 수 없다.</p>
 */
public enum AssetEventBasis {

    /** 단리 — {@code 원금 × 연이율 × (기간개월 ÷ 12)} */
    SIMPLE_INTEREST,

    /** 복리 — 만기에 한 번. 정수 연차는 복리로, 남는 개월은 단리로 계산한다. */
    COMPOUND_INTEREST,

    /** 만기 원금 반환. 이자가 아니라 원래 넣은 돈이 돌아오는 것이다. */
    PRINCIPAL_RETURN
}
