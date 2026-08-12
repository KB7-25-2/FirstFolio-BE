package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.HoldingStatus;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TradeAmounts;
import org.firstfolio.portfolio.domain.TradeCosts;
import org.firstfolio.portfolio.domain.TradePolicy;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.service.CurrentPriceReader;
import org.firstfolio.simulation.service.TradingHours;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 모의 상품 매수·매도 (FUNC-035).
 *
 * <h3>매수는 금액, 매도는 수량</h3>
 *
 * <p>살 때는 "얼마를 넣을까"를 고르고 팔 때는 "무엇을 얼마나 뺄까"를 고른다. 매수는 현금이 기준이라
 * 금액이, 매도는 보유가 기준이라 수량이 자연스럽다. 매도를 금액으로 받으면 정수 주수 내림 때문에
 * <b>"다 팔았는데 1주 남는"</b> 일이 생긴다.</p>
 *
 * <h3>세 테이블이 한 단위로 움직인다</h3>
 *
 * <p>현금({@code portfolios}) · 보유({@code portfolio_holdings}) · 이력({@code portfolio_transactions})이
 * <b>전부 반영되거나 전부 취소</b>돼야 한다. 하나만 반영되면 사용자 자산이 어긋나는데,
 * 조회 API로는 드러나지 않는다.</p>
 *
 * <h3>현금은 DB가 직접 뺀다</h3>
 *
 * <p>{@link PortfolioMapper#decreaseCash}가 {@code cash_balance >= amount} 조건을 SQL에 담고 있다.
 * 자바에서 계산해 덮어쓰면 같은 사용자의 동시 요청이 <b>보유 현금보다 많이 살 수 있다.</b></p>
 *
 * <h3>체결액과 현금 증감은 다르다</h3>
 *
 * <p>수수료·세금이 붙기 때문이다 (v3 3.3절). 현금에 넣고 빼는 값은 언제나
 * {@link TradeCosts#getNetCashAmount()}이고, 이력의 {@code amount}와 보유 원금에는
 * <b>체결액</b>이 들어간다 — 비용은 매입원가가 아니다.</p>
 *
 * <p>요율은 <b>거래 한 건에 한 번만</b> 읽는다. 매수·매도 경로에서 따로 읽으면 같은 트랜잭션
 * 안에서 정책 버전이 갈릴 수 있다.</p>
 */
@Service
public class TradeService {

    /** 가입형 보유의 수량. 1인 1계좌라 언제나 1이다 — 평가는 원금으로 하므로 값 자체는 쓰이지 않는다. */
    private static final BigDecimal SUBSCRIPTION_QUANTITY = new BigDecimal("1.000000");

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final String EMPTY_TERMS = "{}";

    private static final Logger log = LogManager.getLogger(TradeService.class);
    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();

    private final PortfolioMapper portfolioMapper;
    private final PortfolioHoldingMapper holdingMapper;
    private final PortfolioTransactionMapper transactionMapper;
    private final FinancialProductMapper productMapper;
    private final CurrentPriceReader priceReader;
    private final TradeCalculator calculator;
    private final TradingHours tradingHours;
    private final AssetEventScheduler eventScheduler;
    private final TradePolicyProvider tradePolicyProvider;

    public TradeService(
            PortfolioMapper portfolioMapper,
            PortfolioHoldingMapper holdingMapper,
            PortfolioTransactionMapper transactionMapper,
            FinancialProductMapper productMapper,
            CurrentPriceReader priceReader,
            TradeCalculator calculator,
            TradingHours tradingHours,
            AssetEventScheduler eventScheduler,
            TradePolicyProvider tradePolicyProvider
    ) {
        this.portfolioMapper = portfolioMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
        this.productMapper = productMapper;
        this.priceReader = priceReader;
        this.calculator = calculator;
        this.tradingHours = tradingHours;
        this.eventScheduler = eventScheduler;
        this.tradePolicyProvider = tradePolicyProvider;
    }

    @Transactional
    public TradeResult trade(Long userId, TradeCommand command) {
        requireIdempotencyKey(command);

        PortfolioTransaction done =
                transactionMapper.findByIdempotencyKey(command.getIdempotencyKey());

        if (done != null) {
            return replay(done, command);
        }

        Portfolio portfolio = requireActivePortfolio(userId);
        FinancialProduct product = requireActiveProduct(command.getProductId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        requireMarketOpen(product.getAssetType(), now);

        PortfolioHolding holding = holdingMapper.findByPortfolioAndProduct(
                portfolio.getPortfolioId(),
                product.getProductId()
        );

        // 요율은 거래당 한 번만 읽는다. 매수·매도 경로에서 따로 읽으면 버전이 갈릴 수 있다.
        TradePolicy policy = tradePolicyProvider.findAt(now);

        Executed executed = command.isBuy()
                ? buy(portfolio, product, holding, command, now, policy)
                : sell(portfolio, product, holding, command, now, policy);

        TradeAmounts amounts = executed.amounts;

        // 갱신된 잔액을 다시 읽는다 — 차감을 DB가 했으므로 자바에 정확한 값이 없다.
        Portfolio updated = portfolioMapper.findById(portfolio.getPortfolioId());
        PortfolioHolding stored = holdingMapper.findByPortfolioAndProduct(
                portfolio.getPortfolioId(),
                product.getProductId()
        );
        PortfolioTransaction record =
                record(portfolio, product, stored, command, amounts, executed.costs, now);

        if (command.isBuy()) {
            // 만기까지의 이자·만기 일정을 여기서 전부 만든다. 매수 이력이 있어야 event_key를
            // 유일하게 만들 수 있어 이력 기록 뒤에 부른다 (FUNC-041).
            eventScheduler.schedule(product, stored, record, amounts.getExecutedAmount(), now);
        }

        log.info(
                "거래 체결 userId={} type={} productId={} 요청={} 체결={} 수수료={} 현금증감={} 잔액={}",
                userId,
                command.getTransactionType(),
                product.getProductId(),
                amounts.getRequestedAmount(),
                amounts.getExecutedAmount(),
                executed.costs.getFeeAmount(),
                executed.costs.getNetCashAmount(),
                updated.getCashBalance()
        );

        return toResult(record, amounts, executed.costs, updated.getCashBalance());
    }

    // ---------------------------------------------------------------- 매수

    /**
     * <b>수수료는 체결액 밖에서 더 나간다.</b> 그래서 잔액을 전부 넣는 요청은 수수료만큼 모자라
     * 거부될 수 있다 — 잔액 판정은 여기서도 DB가 한다.
     *
     * <p>보유 원금에는 체결액만 넣는다. 수수료는 비용이지 매입원가가 아니다.</p>
     */
    private Executed buy(
            Portfolio portfolio,
            FinancialProduct product,
            PortfolioHolding holding,
            TradeCommand command,
            LocalDateTime now,
            TradePolicy policy
    ) {
        requireAmount(command);
        requireNoQuantity(command);

        TradeAmounts amounts = isPriceBased(product.getAssetType())
                ? buyPriceBased(product, command)
                : buySubscription(holding, command);

        TradeCosts costs = calculator.costsForBuy(
                product.getAssetType(), amounts.getExecutedAmount(), policy);

        decreaseCash(portfolio, costs.getNetCashAmount(), now);
        upsertHoldingAfterBuy(portfolio, product, holding, amounts, now);

        return new Executed(amounts, costs);
    }

    private TradeAmounts buyPriceBased(FinancialProduct product, TradeCommand command) {
        TradeAmounts amounts =
                calculator.buyByAmount(command.getAmount(), requireCurrentPrice(product));

        // 요청 금액이 1주 값보다 적으면 살 것이 없다. 최소 구매 금액을 두지 않았으므로 이것이 하한이다.
        if (amounts.getQuantity().signum() == 0) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "현재가보다 적은 금액으로는 매수할 수 없습니다."
            );
        }

        return amounts;
    }

    /** 가입형은 1인 1계좌다. 다만 <b>해지한 뒤에는 다시 가입할 수 있다.</b> */
    private TradeAmounts buySubscription(PortfolioHolding holding, TradeCommand command) {
        if (holding != null && holding.getStatus() == HoldingStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "이미 가입한 상품입니다.");
        }

        return calculator.subscribe(command.getAmount());
    }

    private void upsertHoldingAfterBuy(
            Portfolio portfolio,
            FinancialProduct product,
            PortfolioHolding holding,
            TradeAmounts amounts,
            LocalDateTime now
    ) {
        boolean priceBased = isPriceBased(product.getAssetType());
        BigDecimal boughtQuantity = priceBased ? amounts.getQuantity() : SUBSCRIPTION_QUANTITY;

        if (holding == null) {
            PortfolioHolding created = new PortfolioHolding();

            created.setPortfolioId(portfolio.getPortfolioId());
            created.setProductId(product.getProductId());
            created.setQuantity(boughtQuantity);
            created.setPrincipalAmount(amounts.getExecutedAmount());
            created.setAverageCost(priceBased ? amounts.getUnitPrice() : null);
            created.setTermsSnapshotJson(termsSnapshot(product));
            created.setStatus(HoldingStatus.ACTIVE);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);

            holdingMapper.insert(created);

            return;
        }

        // 이미 있는 행을 되살린다. uq_portfolio_holdings_product 때문에 새로 INSERT할 수 없고,
        // 거래 이력이 FK로 참조해 삭제도 못 한다.
        boolean reviving = holding.getStatus() != HoldingStatus.ACTIVE;
        BigDecimal heldQuantity = reviving ? BigDecimal.ZERO : holding.getQuantity();
        BigDecimal heldPrincipal = reviving ? ZERO_MONEY : holding.getPrincipalAmount();

        holding.setQuantity(heldQuantity.add(boughtQuantity));
        holding.setPrincipalAmount(heldPrincipal.add(amounts.getExecutedAmount()));
        holding.setAverageCost(priceBased
                ? calculator.averageCostAfterBuy(heldQuantity, heldPrincipal, amounts)
                : null);
        holding.setStatus(HoldingStatus.ACTIVE);
        holding.setUpdatedAt(now);

        if (reviving) {
            // 다시 가입·매수하는 것이므로 그 시점 조건으로 갱신한다.
            holding.setTermsSnapshotJson(termsSnapshot(product));
        }

        holdingMapper.update(holding);
    }

    // ---------------------------------------------------------------- 매도

    /** 매도 대금에서 수수료를 <b>빼고</b> 현금에 넣는다. */
    private Executed sell(
            Portfolio portfolio,
            FinancialProduct product,
            PortfolioHolding holding,
            TradeCommand command,
            LocalDateTime now,
            TradePolicy policy
    ) {
        requireNoAmount(command);
        PortfolioHolding owned = requireActiveHolding(holding);

        boolean priceBased = isPriceBased(product.getAssetType());
        TradeAmounts amounts = priceBased
                ? sellPriceBased(product, owned, command)
                : redeemSubscription(owned, command);

        TradeCosts costs = calculator.costsForSell(
                product.getAssetType(), amounts.getExecutedAmount(), policy);

        increaseCash(portfolio, costs.getNetCashAmount(), now);
        reduceHolding(owned, amounts, priceBased, now);

        if (owned.getStatus() == HoldingStatus.SOLD) {
            cancelScheduledEvents(owned);
        }

        return new Executed(amounts, costs);
    }

    /**
     * 보유가 닫히면 남은 예정 이벤트를 끊는다 (FUNC-041).
     *
     * <p><b>없으면 이미 판 상품의 이자가 나중에 현금으로 들어온다.</b> 일정을 가입 시점에
     * 미리 만들어 두는 구조라 해지가 정리해 주지 않으면 그대로 살아남는다.
     * 이미 지급된 이력({@code COMPLETED})은 건드리지 않는다 — 실제로 받은 돈이다.</p>
     *
     * <p>주식·펀드는 애초에 예정 이벤트가 없어 언제나 0건이다.</p>
     */
    private void cancelScheduledEvents(PortfolioHolding closed) {
        int cancelled = transactionMapper.cancelScheduledByHolding(closed.getHoldingId());

        if (cancelled > 0) {
            log.info("해지로 예정 이벤트 취소 holdingId={} 건수={}", closed.getHoldingId(), cancelled);
        }
    }

    private TradeAmounts sellPriceBased(
            FinancialProduct product,
            PortfolioHolding holding,
            TradeCommand command
    ) {
        BigDecimal quantity = command.getQuantity();

        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "매도 수량이 필요합니다.");
        }

        if (quantity.compareTo(holding.getQuantity()) > 0) {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "보유 수량을 초과해 매도할 수 없습니다.");
        }

        return calculator.sellByQuantity(quantity, requireCurrentPrice(product));
    }

    /** 가입형은 부분 해지가 없다. 수량을 보내는 것 자체가 잘못된 요청이다. */
    private TradeAmounts redeemSubscription(PortfolioHolding holding, TradeCommand command) {
        if (command.getQuantity() != null) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "이 상품은 부분 해지를 지원하지 않습니다. 수량 없이 요청하면 전량 해지됩니다."
            );
        }

        return calculator.redeem(holding);
    }

    private void reduceHolding(
            PortfolioHolding holding,
            TradeAmounts amounts,
            boolean priceBased,
            LocalDateTime now
    ) {
        BigDecimal soldQuantity = priceBased ? amounts.getQuantity() : holding.getQuantity();
        BigDecimal remainingQuantity = holding.getQuantity().subtract(soldQuantity);

        holding.setPrincipalAmount(priceBased
                ? calculator.remainingPrincipalAfterSell(
                        holding.getQuantity(), holding.getPrincipalAmount(), soldQuantity)
                : ZERO_MONEY);
        holding.setQuantity(remainingQuantity);
        // 평균 매입 단가는 그대로 둔다 — 판다고 매입 단가가 달라지지는 않는다.
        holding.setStatus(remainingQuantity.signum() > 0 ? HoldingStatus.ACTIVE : HoldingStatus.SOLD);
        holding.setUpdatedAt(now);

        holdingMapper.update(holding);
    }

    // ---------------------------------------------------------------- 현금·이력

    private void decreaseCash(Portfolio portfolio, BigDecimal amount, LocalDateTime now) {
        if (portfolioMapper.decreaseCash(portfolio.getPortfolioId(), amount, now) == 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_SIMULATION_CASH);
        }
    }

    private void increaseCash(Portfolio portfolio, BigDecimal amount, LocalDateTime now) {
        if (portfolioMapper.increaseCash(portfolio.getPortfolioId(), amount, now) == 0) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "포트폴리오 상태가 바뀌어 거래를 완료하지 못했습니다."
            );
        }
    }

    private PortfolioTransaction record(
            Portfolio portfolio,
            FinancialProduct product,
            PortfolioHolding stored,
            TradeCommand command,
            TradeAmounts amounts,
            TradeCosts costs,
            LocalDateTime now
    ) {
        PortfolioTransaction transaction = new PortfolioTransaction();

        transaction.setPortfolioId(portfolio.getPortfolioId());
        transaction.setHoldingId(stored == null ? null : stored.getHoldingId());
        transaction.setProductId(product.getProductId());
        transaction.setTransactionType(command.getTransactionType());
        transaction.setAmount(amounts.getExecutedAmount());
        transaction.setQuantity(amounts.getQuantity());
        transaction.setUnitPrice(amounts.getUnitPrice());
        // v3 3.2절의 "주문 후 30초 체결"은 2차 이월이라 즉시 완료로 둔다.
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedAt(now);
        transaction.setIdempotencyKey(command.getIdempotencyKey());
        transaction.setDetailJson(describe(command, amounts, costs));
        transaction.setCreatedAt(now);

        transactionMapper.insert(transaction);

        return transaction;
    }

    /**
     * 요청 스냅샷과 체결 근거.
     *
     * <p>스냅샷이 있어야 <b>같은 키로 다른 내용이 왔을 때</b> 구분할 수 있다 (409).
     * 없으면 무엇을 요청했었는지 알 방법이 없어 전부 같은 요청으로 취급하게 된다.</p>
     *
     * <p>비용은 <b>금액과 요율을 함께</b> 남긴다. 요율은 {@code system_policies}로 관리되어 언제든
     * 바뀌므로 금액만 남기면 나중에 검산할 수 없다. {@code policy_version}이 null이면 저장된 정책
     * 없이 설정 기본값으로 계산했다는 뜻이고, 그 사실도 이력에 남아야 한다.</p>
     */
    private static String describe(TradeCommand command, TradeAmounts amounts, TradeCosts costs) {
        ObjectNode detail = OBJECT_MAPPER.createObjectNode();
        ObjectNode request = detail.putObject("request");

        request.put("transaction_type", command.getTransactionType().name());
        request.put("product_id", command.getProductId());
        request.put("amount", text(command.getAmount()));
        request.put("quantity", text(command.getQuantity()));

        detail.put("requested_amount", amounts.getRequestedAmount().toPlainString());
        detail.put("executed_amount", amounts.getExecutedAmount().toPlainString());
        detail.put("fee_rate", costs.getFeeRate().toPlainString());
        detail.put("fee_amount", costs.getFeeAmount().toPlainString());
        detail.put("tax_rate", costs.getTaxRate().toPlainString());
        detail.put("tax_amount", costs.getTaxAmount().toPlainString());
        detail.put("net_cash_amount", costs.getNetCashAmount().toPlainString());
        detail.put("policy_version", costs.getPolicyVersion());

        return detail.toString();
    }

    // ---------------------------------------------------------------- 멱등

    /**
     * 같은 키로 다시 온 요청.
     *
     * <p>내용이 같으면 그때 결과를 그대로 돌려주고, 다르면 {@code 409}다. 같은 키에 다른 거래를
     * 붙이면 <b>둘 중 하나가 조용히 사라진다.</b></p>
     *
     * <p>수수료도 <b>이력에 남긴 값을 그대로 복원</b>한다. 지금 요율로 다시 계산하면 그 사이 정책이
     * 바뀌었을 때 같은 거래가 두 가지 값으로 보인다.</p>
     */
    private TradeResult replay(PortfolioTransaction done, TradeCommand command) {
        if (!sameRequest(done.getDetailJson(), command)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        Portfolio portfolio = portfolioMapper.findById(done.getPortfolioId());

        log.info("이미 처리된 거래 요청입니다 key={}", command.getIdempotencyKey());

        return new TradeResult(
                done.getPortfolioTransactionId(),
                done.getTransactionType().name(),
                done.getProductId(),
                requestedAmountOf(done),
                done.getAmount(),
                moneyOf(done, "fee_amount", ZERO_MONEY),
                moneyOf(done, "net_cash_amount", done.getAmount()),
                done.getQuantity(),
                done.getUnitPrice(),
                done.getStatus().name(),
                portfolio == null ? null : portfolio.getCashBalance()
        );
    }

    /**
     * 이력의 {@code detail_json}에서 금액 하나를 꺼낸다. <b>없으면 기본값</b>이다.
     *
     * <p>수수료를 도입하기 전에 쌓인 이력에는 이 키들이 없다. 그때 거래는 실제로 수수료가 0이었고
     * 현금 증감이 곧 체결액이었으므로, 기본값이 그 사실을 그대로 나타낸다.</p>
     */
    private static BigDecimal moneyOf(PortfolioTransaction done, String field, BigDecimal fallback) {
        JsonNode node = readDetail(done.getDetailJson()).get(field);

        if (node == null || node.isNull()) {
            return fallback;
        }

        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            log.warn("거래 이력의 {}를 읽지 못했습니다 transactionId={}",
                    field, done.getPortfolioTransactionId(), exception);

            return fallback;
        }
    }

    private static boolean sameRequest(String detailJson, TradeCommand command) {
        JsonNode request = readDetail(detailJson).get("request");

        if (request == null) {
            return false;
        }

        return request.path("transaction_type").asText(null) != null
                && command.getTransactionType().name().equals(request.path("transaction_type").asText())
                && sameLong(request.path("product_id"), command.getProductId())
                && sameDecimal(request.path("amount"), command.getAmount())
                && sameDecimal(request.path("quantity"), command.getQuantity());
    }

    private static BigDecimal requestedAmountOf(PortfolioTransaction done) {
        JsonNode requested = readDetail(done.getDetailJson()).get("requested_amount");

        return requested == null || requested.isNull()
                ? done.getAmount()
                : new BigDecimal(requested.asText());
    }

    private static JsonNode readDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }

        try {
            return OBJECT_MAPPER.readTree(detailJson);
        } catch (Exception exception) {
            log.warn("거래 상세를 읽지 못했습니다", exception);

            return OBJECT_MAPPER.createObjectNode();
        }
    }

    // ---------------------------------------------------------------- 검증

    private Portfolio requireActivePortfolio(Long userId) {
        Portfolio portfolio = portfolioMapper.findActiveByUserId(userId);

        if (portfolio == null) {
            throw new ApiException(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND);
        }

        return portfolio;
    }

    /** 비공개 상품은 보유할 수 없으므로 거래도 할 수 없다 (FUNC-032). */
    private FinancialProduct requireActiveProduct(Long productId) {
        if (productId == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "상품 식별자가 필요합니다.");
        }

        FinancialProduct product = productMapper.findActiveById(productId);

        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return product;
    }

    private static PortfolioHolding requireActiveHolding(PortfolioHolding holding) {
        if (holding == null || holding.getStatus() != HoldingStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "보유하지 않은 상품입니다.");
        }

        return holding;
    }

    private void requireMarketOpen(AssetType assetType, LocalDateTime now) {
        if (!tradingHours.isOpen(assetType, now)) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "거래 가능 시간이 아닙니다. (평일 09:00~15:30)"
            );
        }
    }

    /** 가격이 없으면 거래하지 않는다. 임의 값으로 체결하면 사용자 자산이 사실과 달라진다 (FUNC-036). */
    private BigDecimal requireCurrentPrice(FinancialProduct product) {
        ProductPrice price = priceReader.read(product.getProductId());

        if (price == null || price.getPrice() == null || price.getPrice().signum() <= 0) {
            throw new ApiException(
                    ErrorCode.TRADE_NOT_ALLOWED,
                    "현재 시세를 가져올 수 없어 거래할 수 없습니다."
            );
        }

        return price.getPrice();
    }

    private static void requireIdempotencyKey(TradeCommand command) {
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "중복 거래 방지 키가 필요합니다.");
        }

        if (command.getTransactionType() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "거래 유형이 필요합니다.");
        }
    }

    private static void requireAmount(TradeCommand command) {
        if (command.getAmount() == null || command.getAmount().signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "거래 금액이 필요합니다.");
        }
    }

    private static void requireNoQuantity(TradeCommand command) {
        if (command.getQuantity() != null) {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "매수는 금액으로만 요청합니다.");
        }
    }

    private static void requireNoAmount(TradeCommand command) {
        if (command.getAmount() != null) {
            throw new ApiException(ErrorCode.TRADE_NOT_ALLOWED, "매도는 금액으로 요청할 수 없습니다.");
        }
    }

    // ---------------------------------------------------------------- 보조

    private static boolean isPriceBased(AssetType assetType) {
        return assetType != null && !assetType.isTimeCompressed();
    }

    /**
     * 가입·매수 당시 조건. 상품 조건이 나중에 바뀌어도 이 값으로 계산한다.
     *
     * <p>만기·이자 이벤트(FUNC-041)가 이 스냅샷을 읽는다. 압축 대상이 아닌 주식·펀드는 담을 조건이 없다.</p>
     */
    private static String termsSnapshot(FinancialProduct product) {
        String terms = product.getSimulationTermsJson();

        return terms == null || terms.isBlank() ? EMPTY_TERMS : terms;
    }

    private static TradeResult toResult(
            PortfolioTransaction record,
            TradeAmounts amounts,
            TradeCosts costs,
            BigDecimal cashBalance
    ) {
        return new TradeResult(
                record.getPortfolioTransactionId(),
                record.getTransactionType().name(),
                record.getProductId(),
                amounts.getRequestedAmount(),
                amounts.getExecutedAmount(),
                costs.getFeeAmount(),
                costs.getNetCashAmount(),
                amounts.getQuantity(),
                amounts.getUnitPrice(),
                record.getStatus().name(),
                cashBalance
        );
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static boolean sameLong(JsonNode node, Long value) {
        return value == null ? node.isNull() : node.asLong() == value;
    }

    private static boolean sameDecimal(JsonNode node, BigDecimal value) {
        if (value == null) {
            return node.isNull() || node.isMissingNode();
        }

        return !node.isNull() && value.compareTo(new BigDecimal(node.asText())) == 0;
    }

    /**
     * 체결 수치와 비용을 함께 돌려주기 위한 묶음.
     *
     * <p>매수·매도 경로가 현금을 직접 움직이므로 비용도 그 안에서 계산된다. 그런데 이력을 남기는 쪽은
     * 바깥이라 둘 다 필요하다. {@code TradeService} 밖으로 나가지 않는 내부 사정이라 별도 타입으로
     * 만들지 않는다.</p>
     */
    private static final class Executed {

        private final TradeAmounts amounts;
        private final TradeCosts costs;

        private Executed(TradeAmounts amounts, TradeCosts costs) {
            this.amounts = amounts;
            this.costs = costs;
        }
    }
}
