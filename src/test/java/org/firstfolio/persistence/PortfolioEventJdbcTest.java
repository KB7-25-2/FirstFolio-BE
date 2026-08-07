package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.service.PortfolioEventBatchResult;
import org.firstfolio.portfolio.service.PortfolioEventResult;
import org.firstfolio.portfolio.service.PortfolioEventService;
import org.firstfolio.portfolio.service.TradeCommand;
import org.firstfolio.portfolio.service.TradeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 자산 이벤트 배치를 실제 MySQL에 붙여서 확인한다 (FUNC-041).
 *
 * <h3>이 테스트만 롤백하지 않는다</h3>
 *
 * <p>다른 {@code @Tag("jdbc")} 테스트는 트랜잭션 하나로 감싸고 끝에 롤백해서 흔적을 남기지 않는다.
 * <b>여기서는 그 방식을 쓸 수 없다.</b> 검증 대상이 바로 "이벤트마다 트랜잭션이 새로 열린다"는
 * 것인데({@code REQUIRES_NEW}), 그러면:</p>
 *
 * <ul>
 *   <li>새 트랜잭션은 바깥 트랜잭션을 <b>중단시키고</b> 자기 것을 커밋한다 — 롤백이 닿지 않는다</li>
 *   <li>준비 데이터가 바깥 트랜잭션에 잡혀 있으면 안쪽 UPDATE가 <b>행 잠금에서 멈춘다</b></li>
 * </ul>
 *
 * <p>그래서 데이터를 커밋해 넣고 {@code finally}에서 지운다. 상품({@code financial_products})은
 * 팀 공용 시드라 <b>건드리지 않는다</b> — 만든 사용자·포트폴리오·보유·이력만 지운다.</p>
 *
 * <h3>빈을 컨텍스트에서 꺼낸다</h3>
 *
 * <p>다른 jdbc 테스트처럼 {@code new}로 조립하면 <b>프록시가 없어 트랜잭션이 아예 걸리지 않는다.</b>
 * 그러면 "건별 트랜잭션"을 검증한다면서 트랜잭션 없이 도는 코드를 확인하게 된다.</p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class PortfolioEventJdbcTest {

    private static final String INITIAL_CASH = "30000000.00";
    private static final String PRINCIPAL = "10000000.00";

    @Test
    @DisplayName("배치를 두 번 돌려도 현금은 한 번만 는다")
    void appliesEachEventExactlyOnce() throws Exception {
        run((context, connection, fixture) -> {
            PortfolioEventService events = context.getBean(PortfolioEventService.class);

            subscribe(context, fixture, depositId(connection));
            makeAllEventsDue(connection, fixture);

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String beforeCash = cashOf(connection, fixture.portfolioId);

            PortfolioEventBatchResult first = events.process(now, 500);

            assertEquals(2, first.getCompletedCount(), "예·적금은 이자 1회 + 만기 1회입니다.");

            String afterFirst = cashOf(connection, fixture.portfolioId);

            assertNotEquals(beforeCash, afterFirst, "현금이 늘어야 합니다.");

            // 스케줄러 재시도·수동 재실행이 겹쳐도 두 번 들어오면 안 된다.
            PortfolioEventBatchResult second = events.process(now, 500);

            assertEquals(0, second.getProcessedCount(),
                    "완료된 이벤트는 도래분 조회에서 빠져야 합니다.");
            assertEquals(afterFirst, cashOf(connection, fixture.portfolioId),
                    "배치를 다시 돌렸는데 현금이 또 늘었습니다.");
        });
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 성공 건이 롤백되지 않는다")
    void isolatesFailureFromCommittedEvents() throws Exception {
        run((context, connection, fixture) -> {
            PortfolioEventService events = context.getBean(PortfolioEventService.class);

            long depositId = depositId(connection);
            long bondId = PortfolioFixtures.activeProductId(connection, "BOND");

            subscribe(context, fixture, depositId);
            subscribe(context, fixture, bondId);
            makeAllEventsDue(connection, fixture);

            // 채권 보유만 죽여 둔다. 취소를 거치지 않았으므로 예정 이벤트는 그대로 살아 있다.
            long bondHoldingId = holdingIdOf(connection, fixture.portfolioId, bondId);
            forceHoldingStatus(connection, bondHoldingId, "SOLD");

            String beforeCash = cashOf(connection, fixture.portfolioId);
            int bondEvents = eventCount(connection, bondHoldingId);

            PortfolioEventBatchResult result =
                    events.process(LocalDateTime.now(ZoneOffset.UTC), 500);

            assertEquals(bondEvents, result.getFailedCount(), "채권 이벤트는 전부 실패해야 합니다.");
            assertEquals(2, result.getCompletedCount(), "예금 이벤트는 그대로 반영돼야 합니다.");

            // 여기가 이 테스트의 전부다 — 실패 건이 성공 건의 현금까지 되돌리면 안 된다.
            assertNotEquals(beforeCash, cashOf(connection, fixture.portfolioId));
            assertEquals(
                    bondEvents,
                    statusCount(connection, bondHoldingId, "FAILED"),
                    "실패한 트랜잭션 안에서 실패를 기록하면 그 기록까지 함께 사라집니다."
            );

            long depositHoldingId = holdingIdOf(connection, fixture.portfolioId, depositId);

            assertEquals(2, statusCount(connection, depositHoldingId, "COMPLETED"));
        });
    }

    @Test
    @DisplayName("만기가 지나면 보유가 MATURED가 되고, 다시 사면 되살아난다")
    void closesHoldingOnMaturityAndAllowsResubscribe() throws Exception {
        run((context, connection, fixture) -> {
            long depositId = depositId(connection);

            subscribe(context, fixture, depositId);

            long holdingId = holdingIdOf(connection, fixture.portfolioId, depositId);

            makeAllEventsDue(connection, fixture);
            context.getBean(PortfolioEventService.class)
                    .process(LocalDateTime.now(ZoneOffset.UTC), 500);

            assertEquals("MATURED", holdingStatus(connection, holdingId));
            assertEquals("0.00", principalOf(connection, holdingId),
                    "원금을 남겨 두면 현금과 보유에서 이중으로 잡힙니다.");

            // 원금 + 이자가 돌아왔으므로 처음보다 현금이 많다.
            assertTrue(
                    new BigDecimal(cashOf(connection, fixture.portfolioId))
                            .compareTo(new BigDecimal(INITIAL_CASH)) > 0,
                    "만기 후 현금이 원금 이상이어야 합니다."
            );

            // 만기된 보유는 uq_portfolio_holdings_product 때문에 되살리는 것 말고 방법이 없다.
            subscribe(context, fixture, depositId);

            assertEquals("ACTIVE", holdingStatus(connection, holdingId));
            assertEquals(holdingId, holdingIdOf(connection, fixture.portfolioId, depositId));
        });
    }

    @Test
    @DisplayName("아직 도래하지 않은 이벤트에는 손대지 않는다")
    void leavesFutureEventsAlone() throws Exception {
        run((context, connection, fixture) -> {
            subscribe(context, fixture, depositId(connection));

            String cashAfterBuy = cashOf(connection, fixture.portfolioId);

            PortfolioEventBatchResult result = context.getBean(PortfolioEventService.class)
                    .process(LocalDateTime.now(ZoneOffset.UTC), 500);

            assertEquals(0, result.getProcessedCount(), "만기는 압축해도 하루 뒤입니다.");
            assertEquals(cashAfterBuy, cashOf(connection, fixture.portfolioId));
        });
    }

    @Test
    @DisplayName("실패한 이벤트를 재처리하면 그때 현금에 반영된다")
    void retriesFailedEventLater() throws Exception {
        run((context, connection, fixture) -> {
            PortfolioEventService events = context.getBean(PortfolioEventService.class);

            long depositId = depositId(connection);

            subscribe(context, fixture, depositId);
            makeAllEventsDue(connection, fixture);

            long holdingId = holdingIdOf(connection, fixture.portfolioId, depositId);

            // 반영할 수 없는 상태로 만들어 실패시킨다.
            forceHoldingStatus(connection, holdingId, "SOLD");
            events.process(LocalDateTime.now(ZoneOffset.UTC), 500);

            String cashWhileFailed = cashOf(connection, fixture.portfolioId);
            String eventKey = anyEventKey(connection, holdingId, TransactionType.INTEREST);

            assertEquals("FAILED", statusOfEvent(connection, eventKey));

            // 원인을 고치고 다시 처리한다.
            forceHoldingStatus(connection, holdingId, "ACTIVE");

            PortfolioEventResult retried = events.retry(eventKey);

            assertEquals("COMPLETED", retried.getStatus());
            assertNotEquals(cashWhileFailed, cashOf(connection, fixture.portfolioId));

            // 두 번째 재처리는 추가 반영 없이 같은 결과만 돌려준다.
            String afterRetry = cashOf(connection, fixture.portfolioId);

            assertEquals("COMPLETED", events.retry(eventKey).getStatus());
            assertEquals(afterRetry, cashOf(connection, fixture.portfolioId));
        });
    }

    // ------------------------------------------------------------------ 준비

    private static void subscribe(
            AnnotationConfigApplicationContext context,
            Fixture fixture,
            long productId
    ) {
        context.getBean(TradeService.class).trade(fixture.userId, new TradeCommand(
                "jdbc-event-" + UUID.randomUUID(),
                TransactionType.BUY,
                productId,
                new BigDecimal(PRINCIPAL),
                null
        ));
    }

    private static long depositId(Connection connection) throws Exception {
        return PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");
    }

    /**
     * 예정 시각을 과거로 당긴다.
     *
     * <p>압축해도 가장 짧은 만기가 하루라 그대로는 아무것도 도래하지 않는다. 시계를 돌릴 수는
     * 없으므로 데이터를 당긴다 — 배치가 보는 것은 {@code scheduled_at}뿐이다.</p>
     */
    private static void makeAllEventsDue(Connection connection, Fixture fixture) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE portfolio_transactions SET scheduled_at = ?"
                        + " WHERE portfolio_id = ? AND status = 'SCHEDULED'"
        )) {
            statement.setString(1,
                    LocalDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0).toString());
            statement.setLong(2, fixture.portfolioId);
            statement.executeUpdate();
        }
    }

    /** 서비스를 거치지 않고 보유 상태만 바꾼다 — 예정 이벤트는 취소되지 않고 남는다. */
    private static void forceHoldingStatus(Connection connection, long holdingId, String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE portfolio_holdings SET status = ? WHERE holding_id = ?"
        )) {
            statement.setString(1, status);
            statement.setLong(2, holdingId);
            statement.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ 조회

    private static String cashOf(Connection connection, long portfolioId) throws Exception {
        return value(connection,
                "SELECT cash_balance FROM portfolios WHERE portfolio_id = ?", portfolioId);
    }

    private static String holdingStatus(Connection connection, long holdingId) throws Exception {
        return value(connection,
                "SELECT status FROM portfolio_holdings WHERE holding_id = ?", holdingId);
    }

    private static String principalOf(Connection connection, long holdingId) throws Exception {
        return value(connection,
                "SELECT principal_amount FROM portfolio_holdings WHERE holding_id = ?", holdingId);
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

    private static int eventCount(Connection connection, long holdingId) throws Exception {
        return Integer.parseInt(value(connection,
                "SELECT COUNT(*) FROM portfolio_transactions"
                        + " WHERE holding_id = ? AND transaction_type IN ('INTEREST','MATURITY')",
                holdingId));
    }

    /** 매수 이력도 같은 보유에 걸려 있으므로 <b>이벤트 유형만</b> 센다. */
    private static int statusCount(Connection connection, long holdingId, String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM portfolio_transactions"
                        + " WHERE holding_id = ? AND status = ?"
                        + "   AND transaction_type IN ('INTEREST','MATURITY')"
        )) {
            statement.setLong(1, holdingId);
            statement.setString(2, status);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

                return resultSet.getInt(1);
            }
        }
    }

    private static String anyEventKey(Connection connection, long holdingId, TransactionType type)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT event_key FROM portfolio_transactions"
                        + " WHERE holding_id = ? AND transaction_type = ? LIMIT 1"
        )) {
            statement.setLong(1, holdingId);
            statement.setString(2, type.name());

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), type + " 이벤트가 있어야 합니다.");

                return resultSet.getString(1);
            }
        }
    }

    private static String statusOfEvent(Connection connection, String eventKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM portfolio_transactions WHERE event_key = ?"
        )) {
            statement.setString(1, eventKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

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
        void run(
                AnnotationConfigApplicationContext context,
                Connection connection,
                Fixture fixture
        ) throws Exception;
    }

    /**
     * 커밋해 넣고 반드시 지운다.
     *
     * <p>{@code finally}에서 지우므로 시나리오가 실패해도 정리된다. 지우는 순서는 참조의 역순이다 —
     * {@code portfolio_transactions}가 보유·포트폴리오를 {@code ON DELETE RESTRICT}로 잡고 있다.</p>
     */
    private static void run(Scenario scenario) throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);

            try (Connection connection = dataSource.getConnection()) {
                Fixture fixture = givenPortfolio(connection);

                try {
                    scenario.run(context, connection, fixture);
                } finally {
                    cleanUp(connection, fixture);
                }
            }
        }
    }

    private static Fixture givenPortfolio(Connection connection) throws Exception {
        long userId = PortfolioFixtures.insertUser(connection);
        long portfolioId =
                PortfolioFixtures.insertPortfolio(connection, userId, 1, INITIAL_CASH);

        return new Fixture(userId, portfolioId);
    }

    private static void cleanUp(Connection connection, Fixture fixture) throws Exception {
        execute(connection,
                "DELETE t FROM portfolio_transactions t"
                        + " JOIN portfolios p ON p.portfolio_id = t.portfolio_id"
                        + " WHERE p.user_id = ?", fixture.userId);
        execute(connection,
                "DELETE h FROM portfolio_holdings h"
                        + " JOIN portfolios p ON p.portfolio_id = h.portfolio_id"
                        + " WHERE p.user_id = ?", fixture.userId);
        execute(connection, "DELETE FROM portfolios WHERE user_id = ?", fixture.userId);
        execute(connection, "DELETE FROM users WHERE user_id = ?", fixture.userId);
    }

    private static void execute(Connection connection, String sql, long parameter) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parameter);
            statement.executeUpdate();
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("portfolioEventJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }

    private static final class Fixture {

        private final long userId;
        private final long portfolioId;

        private Fixture(long userId, long portfolioId) {
            this.userId = userId;
            this.portfolioId = portfolioId;
        }
    }
}
