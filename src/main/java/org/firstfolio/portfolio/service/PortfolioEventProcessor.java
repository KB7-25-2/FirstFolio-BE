package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.portfolio.domain.HoldingStatus;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 자산 이벤트 <b>한 건</b>을 반영한다 (FUNC-041).
 *
 * <h3>이 클래스가 따로 있는 이유 — 실패 격리</h3>
 *
 * <p>API_DOCS가 <i>"개별 실패가 전체 배치의 성공 건을 롤백하지 않게 격리한다"</i>고 요구한다.
 * 그러려면 <b>이벤트 하나가 트랜잭션 하나</b>여야 하는데, {@link PortfolioEventService}가 자기
 * 메서드를 부르는 방식으로는 안 된다 — <b>같은 객체 안의 호출은 프록시를 타지 않아
 * {@code @Transactional}이 아예 걸리지 않는다.</b> 그러면 배치 전체가 트랜잭션 하나가 되거나
 * (호출자가 트랜잭션을 열었을 때) 아무 트랜잭션도 없이 돌게 된다. 둘 다 격리가 깨진다.
 * 빈을 나눠야 프록시를 거친다.</p>
 *
 * <h3>먼저 완료로 표시하고, 그 다음에 반영한다</h3>
 *
 * <p>순서가 뒤집히면 안 된다. 현금을 먼저 늘리고 완료 표시가 0을 돌려주면(= 다른 배치가 이미 처리)
 * 이미 늘어난 현금을 되돌릴 방법이 <b>예외를 던져 롤백하는 것뿐</b>인데, 그러면 정상 동작인
 * 중복 감지가 실패로 기록된다. 완료 표시를 먼저 하면 <b>갱신 행 수 하나로</b> 이 이벤트를
 * 내가 맡았는지 알 수 있고, 이후 작업은 같은 트랜잭션이라 함께 취소된다.</p>
 */
@Component
public class PortfolioEventProcessor {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();
    private static final Logger log = LogManager.getLogger(PortfolioEventProcessor.class);

    private final PortfolioMapper portfolioMapper;
    private final PortfolioHoldingMapper holdingMapper;
    private final PortfolioTransactionMapper transactionMapper;

    public PortfolioEventProcessor(
            PortfolioMapper portfolioMapper,
            PortfolioHoldingMapper holdingMapper,
            PortfolioTransactionMapper transactionMapper
    ) {
        this.portfolioMapper = portfolioMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 이벤트 하나를 현금·보유에 반영한다. <b>이 메서드 전체가 트랜잭션 하나다.</b>
     *
     * <p>{@code REQUIRES_NEW}인 이유는 호출자가 나중에 트랜잭션을 열더라도 격리가 유지되게 하기
     * 위해서다. 지금은 배치 루프에 트랜잭션이 없어 {@code REQUIRED}와 동작이 같지만,
     * 그 사실에 기대면 나중에 루프에 {@code @Transactional}이 붙는 순간 조용히 깨진다.</p>
     *
     * @throws RuntimeException 반영할 수 없는 상태일 때. 던지면 이 트랜잭션만 롤백되고
     *                          <b>호출자가 실패로 기록</b>한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssetEventOutcome apply(PortfolioTransaction event) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 이 이벤트를 내가 맡는다. 0이면 이미 누가 처리했거나 취소된 것이다.
        if (transactionMapper.markCompleted(
                event.getPortfolioTransactionId(), now, completedDetail(event, now)) == 0) {
            log.info("이미 처리된 이벤트입니다 eventKey={}", event.getEventKey());

            return AssetEventOutcome.SKIPPED;
        }

        PortfolioHolding holding = requireActiveHolding(event);

        // 현금은 DB가 직접 더한다. 세대가 닫혀 있으면 0이 온다.
        if (portfolioMapper.increaseCash(event.getPortfolioId(), event.getAmount(), now) == 0) {
            throw new IllegalStateException(
                    "활성 포트폴리오가 아니라 반영할 수 없습니다. portfolioId=" + event.getPortfolioId()
            );
        }

        if (event.getTransactionType() == TransactionType.MATURITY) {
            close(holding, now);
        }

        log.info(
                "자산 이벤트 반영 eventKey={} type={} amount={}",
                event.getEventKey(),
                event.getTransactionType(),
                event.getAmount()
        );

        return AssetEventOutcome.COMPLETED;
    }

    /**
     * 실패를 남긴다. <b>반드시 별도 트랜잭션이어야 한다</b> —
     * 롤백된 트랜잭션 안에서 쓰면 실패 흔적까지 함께 사라진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(PortfolioTransaction event, Exception cause) {
        transactionMapper.markFailed(
                event.getPortfolioTransactionId(), failedDetail(event, cause));
    }

    /**
     * 만기가 온 보유를 닫는다.
     *
     * <p>원금은 방금 현금으로 돌려줬으므로 <b>0으로 비운다.</b> 남겨 두면 평가에서 이중으로 잡힌다.
     * 해지({@code SOLD})와 같은 처리지만 상태만 {@code MATURED}로 다르다 —
     * 판 것이 아니라 만기가 온 것이다.</p>
     */
    private void close(PortfolioHolding holding, LocalDateTime now) {
        holding.setQuantity(BigDecimal.ZERO);
        holding.setPrincipalAmount(ZERO_MONEY);
        holding.setStatus(HoldingStatus.MATURED);
        holding.setUpdatedAt(now);

        holdingMapper.update(holding);
    }

    /**
     * 보유가 살아 있어야 지급한다.
     *
     * <p>해지·초기화가 예정 이벤트를 취소하므로 정상 경로에서는 언제나 {@code ACTIVE}다.
     * 그렇지 않다면 취소가 빠진 것이므로 <b>조용히 넘기지 않고 실패로 드러낸다</b> —
     * 팔아 버린 상품에 이자를 넣는 것보다 실패 건수로 보이는 편이 낫다.</p>
     */
    private PortfolioHolding requireActiveHolding(PortfolioTransaction event) {
        PortfolioHolding holding = holdingMapper.findByPortfolioAndProduct(
                event.getPortfolioId(), event.getProductId());

        if (holding == null || holding.getStatus() != HoldingStatus.ACTIVE) {
            throw new IllegalStateException(
                    "보유하지 않은 상품의 이벤트입니다. eventKey=" + event.getEventKey()
            );
        }

        return holding;
    }

    /** 예정 시점의 계산 근거를 <b>남긴 채</b> 처리 결과를 덧붙인다. */
    private static String completedDetail(PortfolioTransaction event, LocalDateTime processedAt) {
        ObjectNode detail = detailOf(event);

        detail.put("processed_at", processedAt.toString());
        detail.remove("error");

        return detail.toString();
    }

    private static String failedDetail(PortfolioTransaction event, Exception cause) {
        ObjectNode detail = detailOf(event);

        detail.put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        detail.put("failed_at", LocalDateTime.now(ZoneOffset.UTC).toString());

        return detail.toString();
    }

    private static ObjectNode detailOf(PortfolioTransaction event) {
        String detailJson = event.getDetailJson();

        if (detailJson == null || detailJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(detailJson);

            return node.isObject() ? (ObjectNode) node : OBJECT_MAPPER.createObjectNode();
        } catch (Exception exception) {
            // 근거를 못 읽는다고 지급을 막지는 않는다. 금액은 이미 확정돼 있다.
            log.warn("이벤트 근거를 읽지 못했습니다 eventKey={}", event.getEventKey(), exception);

            return OBJECT_MAPPER.createObjectNode();
        }
    }
}
