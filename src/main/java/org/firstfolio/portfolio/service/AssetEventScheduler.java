package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.ScheduledAssetEvent;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.BondRealTerms;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.RealTerms;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 가입 시점에 만기까지의 이자·만기 이벤트를 <b>전부 미리 만들어 둔다</b> (FUNC-041).
 *
 * <h3>왜 미리 만드나</h3>
 *
 * <p>FUNC-034가 <i>"예정 이벤트를 제공한다"</i>고 요구하고, 스키마에도
 * {@code status = SCHEDULED}와 {@code scheduled_at} 자리가 있다. 미리 만들어 두면
 * 예정 이벤트 조회가 그냥 SELECT가 되고, 실패한 이벤트를 다시 처리할 행도 생긴다.
 * 배치가 그때그때 계산해 넣는 방식이면 둘 다 없다.</p>
 *
 * <h3>금액까지 이때 확정한다</h3>
 *
 * <p>원금과 이율이 가입 시점에 고정되므로 나중에 다시 계산할 이유가 없다. 무엇보다
 * <b>이율은 {@code real_terms}에 있는데 보유의 조건 스냅샷에는 담기지 않는다.</b>
 * 처리 시점에 계산하려면 상품 테이블을 다시 읽어야 하고, 그 사이 관리자가 조건을 바꾸면
 * 가입 당시와 다른 이율로 지급된다.</p>
 *
 * <h3>{@code event_key}</h3>
 *
 * <pre>interest-{보유}-{매수 거래}-{예정 시각}</pre>
 *
 * <p>API_DOCS 예시({@code interest-8101-20260729T0300Z})에 <b>매수 거래 식별자를 더했다.</b>
 * 해지한 상품을 같은 초에 다시 가입하면 보유와 예정 시각이 완전히 겹쳐
 * {@code uq_portfolio_transactions_event_key} 위반으로 <b>매수 자체가 롤백</b>되기 때문이다.
 * 이 값은 재처리 경로에 그대로 실려 돌아오는 불투명한 문자열이라 형식이 바뀌어도 계약에 영향이 없다.</p>
 */
@Component
public class AssetEventScheduler {

    /** API_DOCS 예시의 표기. 경로 변수로 쓰이므로 URL에 안전한 문자만 쓴다. */
    private static final DateTimeFormatter EVENT_KEY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm'Z'");

    /** 계산식에 확정 정책이 없다는 사실을 데이터에 남긴다. 나중에 정책이 정해지면 검산할 수 있어야 한다. */
    private static final String ASSUMPTION =
            "이자 계산식은 확정 정책이 없어 가정치를 적용했다 (FSD FUNC-041 \"별도 정책으로 관리\").";

    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();
    private static final Logger log = LogManager.getLogger(AssetEventScheduler.class);

    private final PortfolioTransactionMapper transactionMapper;
    private final AssetEventCalculator calculator;

    public AssetEventScheduler(
            PortfolioTransactionMapper transactionMapper,
            AssetEventCalculator calculator
    ) {
        this.transactionMapper = transactionMapper;
        this.calculator = calculator;
    }

    /**
     * 매수 직후에 부른다. <b>같은 트랜잭션 안이어야 한다</b> — 일정 없이 가입만 되면
     * 사용자는 만기까지 기다린 뒤에야 아무 일도 일어나지 않는다는 것을 알게 된다.
     *
     * <p>주식·펀드는 만기가 없어 만들 일정이 없다. 배당은 2차 이월이다(이슈 #24).</p>
     *
     * @param buy       방금 남긴 매수 이력. {@code event_key}를 유일하게 만드는 데 쓴다.
     * @param principal 가입 원금(체결 금액)
     * @param openedAt  가입 시각(UTC). 모든 예정 시각의 기준점이다.
     * @return 만든 예정 이벤트 수. 매수형은 0이다.
     */
    public int schedule(
            FinancialProduct product,
            PortfolioHolding holding,
            PortfolioTransaction buy,
            BigDecimal principal,
            LocalDateTime openedAt
    ) {
        AssetType assetType = product.getAssetType();

        if (assetType == null || !assetType.isTimeCompressed()) {
            return 0;
        }

        // TODO: 활성 TRADE 정책의 이자소득세율을 넘긴다 (#77). 지금은 세율 0이라 동작이 이전과 같다.
        List<ScheduledAssetEvent> events = calculator.schedule(
                termsOf(product, holding), principal, openedAt, BigDecimal.ZERO);

        for (ScheduledAssetEvent event : events) {
            transactionMapper.insert(toTransaction(event, product, holding, buy, principal, openedAt));
        }

        log.info(
                "예정 이벤트 생성 holdingId={} productId={} 건수={} 만기={}",
                holding.getHoldingId(),
                product.getProductId(),
                events.size(),
                events.get(events.size() - 1).getScheduledAt()
        );

        return events.size();
    }

    /**
     * 금액은 {@code real_terms}(실제 이율)로, 시각은 보유의 조건 스냅샷(압축된 기간)으로.
     *
     * <p>스냅샷을 쓰는 이유는 상품 조건이 나중에 바뀌어도 <b>가입 당시 기간</b>으로 계산하기
     * 위해서다 ({@code portfolio_holdings.terms_snapshot_json}이 그 목적으로 있는 컬럼이다).</p>
     */
    private static AssetEventTerms termsOf(FinancialProduct product, PortfolioHolding holding) {
        SimulationTerms simulation =
                read(holding.getTermsSnapshotJson(), SimulationTerms.class, product);

        return product.getAssetType() == AssetType.BOND
                ? AssetEventTerms.of(read(product.getRealTermsJson(), BondRealTerms.class, product), simulation)
                : AssetEventTerms.of(read(product.getRealTermsJson(), RealTerms.class, product), simulation);
    }

    /**
     * 조건을 읽지 못하면 <b>가입을 거부한다.</b>
     *
     * <p>비워 두고 넘어가면 이자가 0원인 채로 가입이 성립한다. 가격이 없을 때 거래를 거부하는 것과
     * 같은 이유다 (FUNC-036) — 임의 값을 만들지 않는다.</p>
     */
    private static <T> T read(String rawJson, Class<T> type, FinancialProduct product) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "상품 조건이 없어 가입할 수 없습니다."
            );
        }

        try {
            return OBJECT_MAPPER.readValue(rawJson, type);
        } catch (Exception exception) {
            log.warn("상품 조건을 읽지 못했습니다 productId={}", product.getProductId(), exception);

            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "상품 조건을 읽을 수 없어 가입할 수 없습니다.",
                    exception
            );
        }
    }

    private static PortfolioTransaction toTransaction(
            ScheduledAssetEvent event,
            FinancialProduct product,
            PortfolioHolding holding,
            PortfolioTransaction buy,
            BigDecimal principal,
            LocalDateTime openedAt
    ) {
        String eventKey = eventKeyOf(event, holding, buy);

        PortfolioTransaction scheduled = new PortfolioTransaction();

        scheduled.setPortfolioId(holding.getPortfolioId());
        scheduled.setHoldingId(holding.getHoldingId());
        scheduled.setProductId(product.getProductId());
        scheduled.setTransactionType(event.getType());
        scheduled.setAmount(event.getAmount());
        scheduled.setStatus(TransactionStatus.SCHEDULED);
        scheduled.setScheduledAt(event.getScheduledAt());
        scheduled.setEventKey(eventKey);
        // 두 컬럼 모두 유니크다. 이벤트에는 사용자 요청이 없으므로 같은 값을 쓴다.
        scheduled.setIdempotencyKey(eventKey);
        scheduled.setDetailJson(describe(event, buy, principal));
        scheduled.setCreatedAt(openedAt);

        return scheduled;
    }

    private static String eventKeyOf(
            ScheduledAssetEvent event,
            PortfolioHolding holding,
            PortfolioTransaction buy
    ) {
        return event.getType().name().toLowerCase()
                + "-" + holding.getHoldingId()
                + "-" + buy.getPortfolioTransactionId()
                + "-" + event.getScheduledAt().format(EVENT_KEY_TIME);
    }

    /** 계산 근거. 금액만 남기면 나중에 "왜 이 금액인지"를 확인할 방법이 없다. */
    private static String describe(
            ScheduledAssetEvent event,
            PortfolioTransaction buy,
            BigDecimal principal
    ) {
        ObjectNode detail = OBJECT_MAPPER.createObjectNode();

        detail.put("basis", event.getBasis().name());
        detail.put("principal_amount", principal.toPlainString());
        detail.put("period_months", event.getPeriodMonths());
        detail.put("rate_percent",
                event.getRatePercent() == null ? null : event.getRatePercent().toPlainString());
        detail.put("buy_transaction_id", buy.getPortfolioTransactionId());
        detail.put("assumption", ASSUMPTION);

        return detail.toString();
    }
}
