package org.firstfolio.portfolio.service;

/**
 * 자산 이벤트 한 건을 처리한 결과 (FUNC-041).
 *
 * <p>{@code SKIPPED}가 따로 있는 이유는 <b>실패가 아니기 때문</b>이다. 배치가 겹쳐 돌거나
 * 재처리와 배치가 부딪히면 이미 완료된 이벤트를 다시 집을 수 있는데, 이때 아무것도 하지 않는 것이
 * 정상 동작이다. 실패로 세면 멀쩡한 배치가 실패 건수를 달고 나온다.</p>
 */
public enum AssetEventOutcome {

    /** 현금·보유에 반영하고 완료로 표시했다. */
    COMPLETED,

    /** 이미 처리됐거나 취소된 이벤트라 아무것도 하지 않았다. */
    SKIPPED
}
