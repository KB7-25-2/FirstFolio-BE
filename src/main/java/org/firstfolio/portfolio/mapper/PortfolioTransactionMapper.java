package org.firstfolio.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionType;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PortfolioTransactionMapper {

    /**
     * 같은 요청이 이미 처리됐는지 확인한다. 중복 처리를 막는 1차 방어선이다.
     * (2차는 {@code uq_portfolio_transactions_idempotency} 유니크 제약)
     */
    PortfolioTransaction findByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey
    );

    /**
     * 한 포트폴리오 세대의 거래·자산 이벤트 이력을 최신순으로 읽는다 (FUNC-034).
     *
     * <p>세대가 바뀌면 {@code portfolio_id}가 바뀌므로 이전 세대 이력은 자연히 섞이지 않는다.</p>
     *
     * @param transactionType null이면 전체 유형
     * @param cursorId        null이면 첫 페이지. 있으면 이 식별자보다 앞선(더 오래된) 건부터
     * @param limit           다음 페이지 존재 여부를 알려면 원하는 크기보다 한 건 크게 준다
     */
    List<PortfolioTransaction> findPage(
            @Param("portfolioId") Long portfolioId,
            @Param("transactionType") TransactionType transactionType,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    void insert(PortfolioTransaction transaction);

    // ------------------------------------------------------------------ 자산 이벤트 (FUNC-041)

    /**
     * 처리 시점이 도래한 예정 이벤트를 오래된 것부터 읽는다.
     *
     * <p><b>{@code FAILED}는 담지 않는다.</b> 배치가 실패분까지 다시 집으면 고쳐지지 않는 이벤트
     * 하나가 매 배치마다 같은 실패를 반복하며 로그와 실패 건수를 채운다. 재처리는
     * {@code POST /internal/portfolio-events/{event_key}/retry}로 명시적으로 한다
     * (API_DOCS <i>"FAILED 이벤트만 재처리한다"</i>).</p>
     *
     * <p>정렬에 식별자를 함께 쓰는 이유는 <b>만기 시각에 이자와 만기가 같은 초에 겹치기</b>
     * 때문이다. 예·적금은 만기에 이자와 원금이 함께 나오는데, 만기가 먼저 처리되면 보유가
     * {@code MATURED}로 닫힌 뒤에 이자를 넣게 된다. 일정을 만들 때 이자를 먼저 넣으므로
     * 식별자 오름차순이 곧 "이자 → 만기" 순서다.</p>
     *
     * @param processUntil 이 시각까지 도래한 것만 (요청의 {@code process_until})
     * @param limit        한 번에 처리할 최대 건수 (요청의 {@code batch_size})
     */
    List<PortfolioTransaction> findDueScheduled(
            @Param("processUntil") LocalDateTime processUntil,
            @Param("limit") int limit
    );

    /** 재처리 대상 단건. {@code event_key}는 유니크라 한 건이거나 없다. */
    PortfolioTransaction findByEventKey(@Param("eventKey") String eventKey);

    /**
     * 이벤트를 완료로 표시한다. <b>이 메서드가 자산 이벤트의 멱등성을 지킨다.</b>
     *
     * <p>{@code WHERE status IN ('SCHEDULED','FAILED')}이라 이미 {@code COMPLETED}인 이벤트는
     * 갱신 행이 <b>0</b>이 된다. 배치를 두 번 돌려도 현금이 두 번 늘지 않는다 —
     * {@code decreaseCash}·{@code closeGeneration}과 같은 방식이다.</p>
     *
     * <p>금액은 건드리지 않는다. 지급액은 가입·매수 시점에 확정해 {@code amount}에 넣어두므로
     * 처리 시점에 다시 계산하지 않는다. 두 번 계산하면 두 값이 어긋날 자리가 생긴다.</p>
     *
     * @param detailJson 예정 시점의 계산 근거에 처리 결과를 <b>합친</b> 전체 JSON.
     *                   근거를 지우면 얼마를 왜 줬는지 확인할 방법이 없어진다.
     * @return 갱신된 행 수. 0이면 이미 처리됐거나 취소된 이벤트다.
     */
    int markCompleted(
            @Param("portfolioTransactionId") Long portfolioTransactionId,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("detailJson") String detailJson
    );

    /**
     * 이벤트를 실패로 표시한다. 실패 사유를 {@code detail_json}에 남긴다.
     *
     * <p>재처리에서 또 실패할 수 있으므로 {@code FAILED}도 대상에 넣는다 — 사유가 최신으로 갱신된다.
     * <b>완료된 이벤트를 실패로 되돌리지는 않는다.</b></p>
     */
    int markFailed(
            @Param("portfolioTransactionId") Long portfolioTransactionId,
            @Param("detailJson") String detailJson
    );

    /**
     * 한 보유의 남은 예정 이벤트를 취소한다. <b>중도 해지 시 반드시 호출한다.</b>
     *
     * <p>없으면 이미 판 상품의 이자가 나중에 현금으로 들어온다. 예정 이벤트를 가입 시점에
     * 미리 만들어 두는 구조라 해지가 일정을 정리해 주지 않으면 그대로 살아남는다.</p>
     *
     * @return 취소한 건수
     */
    int cancelScheduledByHolding(@Param("holdingId") Long holdingId);

    /**
     * 한 포트폴리오 세대의 남은 예정 이벤트를 전부 취소한다. 초기화(FUNC-037)에서 쓴다.
     *
     * <p>세대를 닫으면 그 세대의 보유도 함께 정리되므로, 남은 일정도 같은 자리에서 끊는다.</p>
     */
    int cancelScheduledByPortfolio(@Param("portfolioId") Long portfolioId);
}
