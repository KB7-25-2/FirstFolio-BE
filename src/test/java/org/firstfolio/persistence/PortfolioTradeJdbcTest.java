package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.firstfolio.portfolio.service.AssetEventScheduler;
import org.firstfolio.portfolio.service.PortfolioQueryService;
import org.firstfolio.portfolio.service.TradeCalculator;
import org.firstfolio.portfolio.service.TradeCommand;
import org.firstfolio.portfolio.service.TradePolicyProvider;
import org.firstfolio.portfolio.service.TradeResult;
import org.firstfolio.portfolio.service.TradeService;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.service.CurrentPriceReader;
import org.firstfolio.simulation.service.TradingHours;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 매수·매도를 실제 MySQL에 붙여서 확인한다 (FUNC-035).
 *
 * <p><b>이 이슈에서 가장 중요한 검증이다.</b> 거래는 현금·보유·이력 <b>세 테이블을 함께</b> 바꾸는
 * 첫 기능인데, 서비스 테스트는 매퍼를 모킹하므로
 * <b>유니크 제약도 조건부 UPDATE도 실제로 실행되지 않는다.</b></p>
 *
 * <p>특히 <b>전량 매도한 상품을 다시 사는 경로</b>는 실DB에서만 드러난다 —
 * {@code uq_portfolio_holdings_product} 때문에 새로 INSERT할 수 없고,
 * 거래 이력이 FK로 참조해 삭제도 못 한다.</p>
 *
 * <p>거래 시간 판정만 대역으로 바꾼다. 실제 시각에 기대면 <b>장 마감 후에는 테스트가 실패</b>하는데,
 * 여기서 확인하려는 것은 시간 규칙이 아니라 DB에서 무슨 일이 일어나는가다
 * (시간 규칙은 {@code TradingHoursTest}가 지킨다).</p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class PortfolioTradeJdbcTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("100000.0000");

    /** 장 마감 후에도 돌아야 한다. 시간 규칙은 별도 단위 테스트가 지킨다. */
    private static final TradingHours ALWAYS_OPEN = new TradingHours() {
        @Override
        public boolean isOpen(AssetType assetType, LocalDateTime nowUtc) {
            return true;
        }
    };

    @Test
    @DisplayName("매수하면 현금·보유·이력이 함께 바뀐다")
    void buyUpdatesCashHoldingAndHistoryTogether() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long stockId = givenPricedStock(connection);

            TradeResult result = fixture.trade.trade(fixture.userId, buy(stockId, "5000000.00"));

            // 정수 주수 내림 — 50주(500만) 딱 맞아떨어진다.
            assertEquals(new BigDecimal("50.000000"), result.getQuantity());
            assertEquals(new BigDecimal("5000000.00"), result.getAmount());
            // 30,000,000 − 5,000,000(체결) − 750.00(수수료 0.015%)
            assertEquals(new BigDecimal("24999250.00"), result.getCashBalance());

            assertEquals("24999250.00", cashOf(connection, fixture.portfolioId));
            assertEquals(1, holdingCount(connection, fixture.portfolioId));
            assertEquals(1, transactionCount(connection, fixture.portfolioId));

            // 이력이 보유와 이어져 있어야 나중에 추적할 수 있다.
            assertEquals(
                    holdingIdOf(connection, fixture.portfolioId, stockId),
                    Long.valueOf(transactionHoldingId(connection, result.getPortfolioTransactionId()))
            );

            // 조회 API까지 이어지는지 — 사용자가 실제로 보는 화면이다.
            PortfolioDetailResponse detail = fixture.query.findCurrent(fixture.userId);

            assertEquals(1, detail.getHoldings().size());
            assertEquals(new BigDecimal("50.000000"), detail.getHoldings().get(0).getQuantity());
            assertEquals("MARKET_PRICE", detail.getHoldings().get(0).getValuationBasis());
            assertEquals(new BigDecimal("29999250.00"), detail.getSummary().getTotalAssets(),
                    "산 직후 총자산은 수수료만큼만 줄어야 합니다 — 수수료는 실제로 나간 비용이다.");
        });
    }

    @Test
    @DisplayName("전량 매도한 상품을 다시 사면 같은 보유 행을 되살린다")
    void revivesSoldHoldingOnRebuy() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long stockId = givenPricedStock(connection);

            fixture.trade.trade(fixture.userId, buy(stockId, "5000000.00"));
            long firstHoldingId = holdingIdOf(connection, fixture.portfolioId, stockId);

            fixture.trade.trade(fixture.userId, sell(stockId, "50.000000"));

            assertEquals("SOLD", holdingStatus(connection, firstHoldingId));
            // 사고팔면 수수료가 두 번 나간다 — 원금이 그대로 돌아오지 않는다.
            assertEquals("29998500.00", cashOf(connection, fixture.portfolioId));

            // 다시 산다 — 새로 INSERT하면 uq_portfolio_holdings_product에 걸린다.
            TradeResult rebought =
                    fixture.trade.trade(fixture.userId, buy(stockId, "2000000.00"));

            assertEquals(new BigDecimal("20.000000"), rebought.getQuantity());
            assertEquals(
                    firstHoldingId,
                    holdingIdOf(connection, fixture.portfolioId, stockId),
                    "새 행이 아니라 기존 행을 되살려야 합니다."
            );
            assertEquals("ACTIVE", holdingStatus(connection, firstHoldingId));
            assertEquals(1, holdingCount(connection, fixture.portfolioId), "보유 행은 하나뿐이어야 합니다.");
            assertEquals("2000000.00", principalOf(connection, firstHoldingId),
                    "되살릴 때 이전 원금이 남아 있으면 안 됩니다.");
            assertEquals(3, transactionCount(connection, fixture.portfolioId));
        });
    }

    @Test
    @DisplayName("가입형은 보유 중에는 재가입할 수 없고, 해지한 뒤에는 다시 가입할 수 있다")
    void blocksResubscribeWhileHeldAndAllowsAfterRedeem() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long depositId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");

            fixture.trade.trade(fixture.userId, buy(depositId, "10000000.00"));

            assertEquals("20000000.00", cashOf(connection, fixture.portfolioId));

            ApiException blocked = assertThrows(ApiException.class, () ->
                    fixture.trade.trade(fixture.userId, buy(depositId, "5000000.00")));

            assertEquals(ErrorCode.TRADE_NOT_ALLOWED, blocked.getErrorCode());
        });

        // 재가입은 실패한 트랜잭션과 섞이지 않도록 새 트랜잭션에서 확인한다.
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long depositId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");

            fixture.trade.trade(fixture.userId, buy(depositId, "10000000.00"));
            fixture.trade.trade(fixture.userId, sell(depositId, null));

            assertEquals("30000000.00", cashOf(connection, fixture.portfolioId),
                    "해지하면 원금이 전액 돌아와야 합니다.");

            TradeResult again = fixture.trade.trade(fixture.userId, buy(depositId, "7000000.00"));

            assertEquals(new BigDecimal("7000000.00"), again.getAmount());
            assertEquals("23000000.00", cashOf(connection, fixture.portfolioId));
            assertEquals(1, holdingCount(connection, fixture.portfolioId));
        });
    }

    @Test
    @DisplayName("현금이 모자라면 아무것도 바뀌지 않는다")
    void changesNothingWhenCashIsInsufficient() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "1000000.00");
            long stockId = givenPricedStock(connection);

            ApiException exception = assertThrows(ApiException.class, () ->
                    fixture.trade.trade(fixture.userId, buy(stockId, "5000000.00")));

            assertEquals(ErrorCode.INSUFFICIENT_SIMULATION_CASH, exception.getErrorCode());

            // 조건부 UPDATE가 걸러냈으므로 현금도 보유도 이력도 그대로다.
            assertEquals("1000000.00", cashOf(connection, fixture.portfolioId));
            assertEquals(0, holdingCount(connection, fixture.portfolioId));
            assertEquals(0, transactionCount(connection, fixture.portfolioId));
        });
    }

    @Test
    @DisplayName("가입형을 사면 이자·만기 예정 이벤트가 함께 만들어진다")
    void createsScheduledEventsOnSubscription() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long depositId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");

            fixture.trade.trade(fixture.userId, buy(depositId, "10000000.00"));

            long holdingId = holdingIdOf(connection, fixture.portfolioId, depositId);

            // 예·적금은 만기일시지급이라 이자 1회 + 만기 1회다.
            assertEquals(2, scheduledCount(connection, holdingId));

            // 만기에는 원금이 그대로 돌아온다.
            assertEquals("10000000.00", scheduledAmount(connection, holdingId, "MATURITY"));

            // 이자는 0원일 수 없다 — 0원이면 조건을 못 읽고 넘어간 것이다.
            assertTrue(
                    new BigDecimal(scheduledAmount(connection, holdingId, "INTEREST")).signum() > 0,
                    "이자 금액이 0이면 상품 조건을 읽지 못한 것입니다."
            );

            // 아직 오지 않은 이벤트라 처리 시각이 없어야 한다.
            assertNull(value(connection,
                    "SELECT processed_at FROM portfolio_transactions"
                            + " WHERE holding_id = ? AND status = 'SCHEDULED' LIMIT 1", holdingId));

            // 이력 조회에 그대로 실린다 — FUNC-034의 "예정 이벤트를 제공한다"가 이 구조를 전제한다.
            assertEquals(3, transactionCount(connection, fixture.portfolioId), "매수 1 + 예정 2");
        });
    }

    @Test
    @DisplayName("해지하면 남은 예정 이벤트가 취소되고, 다시 가입하면 새 일정이 생긴다")
    void cancelsScheduledEventsOnRedeemAndReschedulesOnResubscribe() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long depositId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");

            fixture.trade.trade(fixture.userId, buy(depositId, "10000000.00"));
            long holdingId = holdingIdOf(connection, fixture.portfolioId, depositId);

            fixture.trade.trade(fixture.userId, sell(depositId, null));

            assertEquals(0, scheduledCount(connection, holdingId),
                    "판 상품의 이자가 나중에 현금으로 들어오면 안 됩니다.");
            assertEquals(2, statusCount(connection, holdingId, "CANCELLED"));

            // 되살린 보유는 같은 행이라, 예정 시각이 겹쳐도 event_key가 부딪히면 안 된다.
            fixture.trade.trade(fixture.userId, buy(depositId, "10000000.00"));

            assertEquals(holdingId, holdingIdOf(connection, fixture.portfolioId, depositId));
            assertEquals(2, scheduledCount(connection, holdingId), "새 일정이 다시 생겨야 합니다.");
        });
    }

    @Test
    @DisplayName("주식·펀드는 만기가 없어 예정 이벤트를 만들지 않는다")
    void createsNoScheduledEventsForPriceBasedProducts() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long stockId = givenPricedStock(connection);

            fixture.trade.trade(fixture.userId, buy(stockId, "5000000.00"));

            assertEquals(0, scheduledCount(
                    connection, holdingIdOf(connection, fixture.portfolioId, stockId)));
            assertEquals(1, transactionCount(connection, fixture.portfolioId), "매수 이력 하나뿐");
        });
    }

    @Test
    @DisplayName("수수료는 저장된 TRADE 정책에서 읽고 근거를 이력에 남긴다")
    void chargesFeeFromStoredPolicyAndRecordsBasis() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long stockId = givenPricedStock(connection);

            TradeResult result = fixture.trade.trade(fixture.userId, buy(stockId, "5000000.00"));
            long transactionId = result.getPortfolioTransactionId();

            // 5,000,000 × 0.00015
            assertEquals("750.00", detailValue(connection, transactionId, "fee_amount"));
            assertEquals("0.00015", detailValue(connection, transactionId, "fee_rate"));
            assertEquals("5000750.00", detailValue(connection, transactionId, "net_cash_amount"));
            assertEquals("5000000.00", detailValue(connection, transactionId, "executed_amount"),
                    "이력의 체결액에는 수수료가 섞이면 안 됩니다.");

            // 저장된 정책을 실제로 지났다는 증거. 기본값 폴백이었다면 null이다.
            assertEquals(
                    value(connection,
                            "SELECT version_no FROM system_policies"
                                    + " WHERE policy_key = 'TRADE' AND is_active = ?"
                                    + " ORDER BY version_no DESC LIMIT 1", 1),
                    detailValue(connection, transactionId, "policy_version")
            );
        });
    }

    @Test
    @DisplayName("가입형은 수수료가 없어 현금이 원금만큼만 준다")
    void chargesNoFeeOnSubscriptionInDatabase() throws Exception {
        withRollback((context, connection) -> {
            Fixture fixture = givenPortfolio(context, connection, "30000000.00");
            long depositId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");

            TradeResult result =
                    fixture.trade.trade(fixture.userId, buy(depositId, "10000000.00"));

            assertEquals("20000000.00", cashOf(connection, fixture.portfolioId));
            assertEquals("0.00",
                    detailValue(connection, result.getPortfolioTransactionId(), "fee_amount"));
        });
    }

    // ------------------------------------------------------------------ 준비

    private static TradeCommand buy(long productId, String amount) {
        return new TradeCommand(
                "jdbc-buy-" + UUID.randomUUID(), TransactionType.BUY,
                productId, new BigDecimal(amount), null
        );
    }

    private static TradeCommand sell(long productId, String quantity) {
        return new TradeCommand(
                "jdbc-sell-" + UUID.randomUUID(), TransactionType.SELL,
                productId, null, quantity == null ? null : new BigDecimal(quantity)
        );
    }

    /** 사용자·포트폴리오와, 실제 매퍼로 조립한 서비스. */
    private static Fixture givenPortfolio(
            AnnotationConfigApplicationContext context,
            Connection connection,
            String cash
    ) throws Exception {
        long userId = PortfolioFixtures.insertUser(connection);
        long portfolioId = PortfolioFixtures.insertPortfolio(connection, userId, 1, cash);

        TradeService trade = new TradeService(
                context.getBean(PortfolioMapper.class),
                context.getBean(PortfolioHoldingMapper.class),
                context.getBean(PortfolioTransactionMapper.class),
                context.getBean(FinancialProductMapper.class),
                context.getBean(CurrentPriceReader.class),
                new TradeCalculator(),
                ALWAYS_OPEN,
                context.getBean(AssetEventScheduler.class),
                // 실제 조회 경로를 지난다 — 저장된 TRADE 정책이 있으면 그것, 없으면 설정 기본값이다.
                context.getBean(TradePolicyProvider.class)
        );

        return new Fixture(userId, portfolioId, trade, context.getBean(PortfolioQueryService.class));
    }

    /** 공개된 주식 하나에 이 테스트 전용 최신 가격을 넣는다. DB에 이미 쌓인 가격에 기대지 않는다. */
    private static long givenPricedStock(Connection connection) throws Exception {
        long stockId = PortfolioFixtures.activeProductId(connection, "STOCK");

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO product_prices ("
                        + "product_id, price, reference_at, source_type, generation_key, created_at"
                        + ") VALUES (?, ?, ?, 'SIMULATION', ?, NOW())"
        )) {
            statement.setLong(1, stockId);
            statement.setBigDecimal(2, UNIT_PRICE);
            statement.setString(3, LocalDateTime.now(ZoneOffset.UTC).withNano(0).toString());
            statement.setString(4, "jdbc-trade-" + UUID.randomUUID());
            statement.executeUpdate();
        }

        return stockId;
    }

    // ------------------------------------------------------------------ 조회

    private static String cashOf(Connection connection, long portfolioId) throws Exception {
        return value(connection,
                "SELECT cash_balance FROM portfolios WHERE portfolio_id = ?", portfolioId);
    }

    private static int holdingCount(Connection connection, long portfolioId) throws Exception {
        return Integer.parseInt(value(connection,
                "SELECT COUNT(*) FROM portfolio_holdings WHERE portfolio_id = ?", portfolioId));
    }

    private static int transactionCount(Connection connection, long portfolioId) throws Exception {
        return Integer.parseInt(value(connection,
                "SELECT COUNT(*) FROM portfolio_transactions WHERE portfolio_id = ?", portfolioId));
    }

    private static long holdingIdOf(Connection connection, long portfolioId, long productId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT holding_id FROM portfolio_holdings"
                        + " WHERE portfolio_id = ? AND product_id = ?"
        )) {
            statement.setLong(1, portfolioId);
            statement.setLong(2, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "보유가 있어야 합니다.");

                return resultSet.getLong(1);
            }
        }
    }

    private static String holdingStatus(Connection connection, long holdingId) throws Exception {
        return value(connection,
                "SELECT status FROM portfolio_holdings WHERE holding_id = ?", holdingId);
    }

    private static String principalOf(Connection connection, long holdingId) throws Exception {
        return value(connection,
                "SELECT principal_amount FROM portfolio_holdings WHERE holding_id = ?", holdingId);
    }

    private static long transactionHoldingId(Connection connection, long transactionId)
            throws Exception {
        return Long.parseLong(value(connection,
                "SELECT holding_id FROM portfolio_transactions WHERE portfolio_transaction_id = ?",
                transactionId));
    }

    /**
     * {@code detail_json}에서 키 하나를 꺼낸다.
     *
     * <p>JSON 컬럼이라 MySQL이 공백·키 순서를 자기 방식으로 정규화한다. 문자열 포함 검사로 보면
     * 형식이 조금만 달라져도 깨지므로 <b>DB에게 경로로 물어본다.</b></p>
     */
    private static String detailValue(Connection connection, long transactionId, String path)
            throws Exception {
        return value(connection,
                "SELECT detail_json ->> '$." + path + "'"
                        + " FROM portfolio_transactions WHERE portfolio_transaction_id = ?",
                transactionId);
    }

    private static int scheduledCount(Connection connection, long holdingId) throws Exception {
        return statusCount(connection, holdingId, "SCHEDULED");
    }

    private static int statusCount(Connection connection, long holdingId, String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM portfolio_transactions"
                        + " WHERE holding_id = ? AND status = ?"
        )) {
            statement.setLong(1, holdingId);
            statement.setString(2, status);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

                return resultSet.getInt(1);
            }
        }
    }

    private static String scheduledAmount(Connection connection, long holdingId, String type)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM portfolio_transactions"
                        + " WHERE holding_id = ? AND transaction_type = ? AND status = 'SCHEDULED'"
        )) {
            statement.setLong(1, holdingId);
            statement.setString(2, type);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), type + " 예정 이벤트가 있어야 합니다.");

                return resultSet.getString(1);
            }
        }
    }

    private static String value(Connection connection, String sql, long parameter) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parameter);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

                return resultSet.getString(1);
            }
        }
    }

    // ------------------------------------------------------------------ 실행 틀

    private interface Scenario {
        void run(AnnotationConfigApplicationContext context, Connection connection) throws Exception;
    }

    private static void withRollback(Scenario scenario) throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                scenario.run(context, DataSourceUtils.getConnection(dataSource));
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("portfolioTradeJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }

    private static final class Fixture {

        private final long userId;
        private final long portfolioId;
        private final TradeService trade;
        private final PortfolioQueryService query;

        private Fixture(long userId, long portfolioId, TradeService trade, PortfolioQueryService query) {
            this.userId = userId;
            this.portfolioId = portfolioId;
            this.trade = trade;
            this.query = query;
            assertNotNull(trade);
        }
    }
}
