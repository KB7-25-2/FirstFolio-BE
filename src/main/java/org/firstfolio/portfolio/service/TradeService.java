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

    public TradeService(
            PortfolioMapper portfolioMapper,
            PortfolioHoldingMapper holdingMapper,
            PortfolioTransactionMapper transactionMapper,
            FinancialProductMapper productMapper,
            CurrentPriceReader priceReader,
            TradeCalculator calculator,
            TradingHours tradingHours
    ) {
        this.portfolioMapper = portfolioMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
        this.productMapper = productMapper;
        this.priceReader = priceReader;
        this.calculator = calculator;
        this.tradingHours = tradingHours;
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

        TradeAmounts amounts = command.isBuy()
                ? buy(portfolio, product, holding, command, now)
                : sell(portfolio, product, holding, command, now);

        // 갱신된 잔액을 다시 읽는다 — 차감을 DB가 했으므로 자바에 정확한 값이 없다.
        Portfolio updated = portfolioMapper.findById(portfolio.getPortfolioId());
        PortfolioTransaction record = record(portfolio, product, command, amounts, now);

        log.info(
                "거래 체결 userId={} type={} productId={} 요청={} 체결={} 잔액={}",
                userId,
                command.getTransactionType(),
                product.getProductId(),
                amounts.getRequestedAmount(),
                amounts.getExecutedAmount(),
                updated.getCashBalance()
        );

        return toResult(record, amounts, updated.getCashBalance());
    }

    // ---------------------------------------------------------------- 매수

    private TradeAmounts buy(
            Portfolio portfolio,
            FinancialProduct product,
            PortfolioHolding holding,
            TradeCommand command,
            LocalDateTime now
    ) {
        requireAmount(command);
        requireNoQuantity(command);

        TradeAmounts amounts = isPriceBased(product.getAssetType())
                ? buyPriceBased(product, command)
                : buySubscription(holding, command);

        decreaseCash(portfolio, amounts.getExecutedAmount(), now);
        upsertHoldingAfterBuy(portfolio, product, holding, amounts, now);

        return amounts;
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

    private TradeAmounts sell(
            Portfolio portfolio,
            FinancialProduct product,
            PortfolioHolding holding,
            TradeCommand command,
            LocalDateTime now
    ) {
        requireNoAmount(command);
        PortfolioHolding owned = requireActiveHolding(holding);

        boolean priceBased = isPriceBased(product.getAssetType());
        TradeAmounts amounts = priceBased
                ? sellPriceBased(product, owned, command)
                : redeemSubscription(owned, command);

        increaseCash(portfolio, amounts.getExecutedAmount(), now);
        reduceHolding(owned, amounts, priceBased, now);

        return amounts;
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
            TradeCommand command,
            TradeAmounts amounts,
            LocalDateTime now
    ) {
        PortfolioHolding stored = holdingMapper.findByPortfolioAndProduct(
                portfolio.getPortfolioId(),
                product.getProductId()
        );

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
        transaction.setDetailJson(describe(command, amounts));
        transaction.setCreatedAt(now);

        transactionMapper.insert(transaction);

        return transaction;
    }

    /**
     * 요청 스냅샷과 체결 근거.
     *
     * <p>스냅샷이 있어야 <b>같은 키로 다른 내용이 왔을 때</b> 구분할 수 있다 (409).
     * 없으면 무엇을 요청했었는지 알 방법이 없어 전부 같은 요청으로 취급하게 된다.</p>
     */
    private static String describe(TradeCommand command, TradeAmounts amounts) {
        ObjectNode detail = OBJECT_MAPPER.createObjectNode();
        ObjectNode request = detail.putObject("request");

        request.put("transaction_type", command.getTransactionType().name());
        request.put("product_id", command.getProductId());
        request.put("amount", text(command.getAmount()));
        request.put("quantity", text(command.getQuantity()));

        detail.put("requested_amount", amounts.getRequestedAmount().toPlainString());
        detail.put("executed_amount", amounts.getExecutedAmount().toPlainString());

        return detail.toString();
    }

    // ---------------------------------------------------------------- 멱등

    /**
     * 같은 키로 다시 온 요청.
     *
     * <p>내용이 같으면 그때 결과를 그대로 돌려주고, 다르면 {@code 409}다. 같은 키에 다른 거래를
     * 붙이면 <b>둘 중 하나가 조용히 사라진다.</b></p>
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
                done.getQuantity(),
                done.getUnitPrice(),
                done.getStatus().name(),
                portfolio == null ? null : portfolio.getCashBalance()
        );
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
            BigDecimal cashBalance
    ) {
        return new TradeResult(
                record.getPortfolioTransactionId(),
                record.getTransactionType().name(),
                record.getProductId(),
                amounts.getRequestedAmount(),
                amounts.getExecutedAmount(),
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
}
