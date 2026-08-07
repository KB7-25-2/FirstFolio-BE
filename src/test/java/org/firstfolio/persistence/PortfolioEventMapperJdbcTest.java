package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TransactionStatus;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 자산 이벤트 매퍼를 실제 MySQL에 붙여서 확인한다 (FUNC-041).
 *
 * <p>여기서 확인하는 것들은 <b>SQL로만 성립하는 규칙</b>이라 매퍼를 모킹한 서비스 테스트로는
 * 한 줄도 검증되지 않는다.</p>
 *
 * <ul>
 *   <li>{@code markCompleted}의 상태 조건 — 배치를 두 번 돌려도 두 번 반영되지 않는다</li>
 *   <li>{@code findDueScheduled}의 정렬 — 만기 시각에 겹치는 이자와 만기의 처리 순서</li>
 *   <li>취소가 <b>예정분만</b> 건드리고 이미 지급된 이력은 남긴다</li>
 * </ul>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class PortfolioEventMapperJdbcTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

    @Test
    @DisplayName("도래한 예정 이벤트만 읽고, 아직 이른 것은 두고 간다")
    void readsOnlyDueScheduledEvents() throws Exception {
        withRollback((mapper, connection) -> {
            Fixture fixture = givenHolding(connection);

            String dueKey = insertScheduled(connection, fixture, NOW.minusHours(1), "10000.00");
            String laterKey = insertScheduled(connection, fixture, NOW.plusHours(1), "10000.00");

            List<PortfolioTransaction> due = mapper.findDueScheduled(NOW, 500);

            assertTrue(keys(due).contains(dueKey), "도래한 이벤트를 읽어야 합니다.");
            assertTrue(!keys(due).contains(laterKey), "아직 이른 이벤트는 읽지 않아야 합니다.");
        });
    }

    @Test
    @DisplayName("같은 시각에 겹치면 먼저 만든 이벤트를 먼저 읽는다")
    void readsEarlierInsertedEventFirstOnTie() throws Exception {
        withRollback((mapper, connection) -> {
            Fixture fixture = givenHolding(connection);
            LocalDateTime maturity = NOW.minusMinutes(1);

            // 만기 시각에는 이자와 만기가 같은 초에 겹친다. 일정을 만들 때 이자를 먼저 넣는다.
            String interestKey = insertScheduled(
                    connection, fixture, maturity, "10000.00", TransactionType.INTEREST);
            String maturityKey = insertScheduled(
                    connection, fixture, maturity, "1000000.00", TransactionType.MATURITY);

            List<String> keys = keys(mapper.findDueScheduled(NOW, 500));

            assertTrue(
                    keys.indexOf(interestKey) < keys.indexOf(maturityKey),
                    "이자가 만기보다 먼저 처리돼야 합니다. 만기가 먼저면 보유가 닫힌 뒤에 이자를 넣게 됩니다."
            );
        });
    }

    @Test
    @DisplayName("완료 표시는 한 번만 먹는다 — 배치를 두 번 돌려도 두 번 반영되지 않는다")
    void marksCompletedOnlyOnce() throws Exception {
        withRollback((mapper, connection) -> {
            Fixture fixture = givenHolding(connection);
            String eventKey = insertScheduled(connection, fixture, NOW.minusHours(1), "10000.00");

            PortfolioTransaction event = mapper.findByEventKey(eventKey);

            assertNotNull(event, "예정 이벤트를 event_key로 찾을 수 있어야 합니다.");
            assertEquals(TransactionStatus.SCHEDULED, event.getStatus());
            assertNull(event.getProcessedAt(), "아직 반영 전이라 처리 시각이 없어야 합니다.");

            long id = event.getPortfolioTransactionId();

            assertEquals(1, mapper.markCompleted(id, NOW, "{\"result\":\"first\"}"));
            assertEquals(0, mapper.markCompleted(id, NOW, "{\"result\":\"second\"}"),
                    "이미 완료된 이벤트는 갱신 행이 0이어야 합니다.");

            PortfolioTransaction completed = mapper.findByEventKey(eventKey);

            assertEquals(TransactionStatus.COMPLETED, completed.getStatus());
            assertEquals(NOW, completed.getProcessedAt());
            assertTrue(completed.getDetailJson().contains("first"),
                    "두 번째 호출이 근거를 덮어쓰지 않아야 합니다.");

            // 도래분 조회에서도 빠진다 — 다음 배치가 다시 집지 않는다.
            assertTrue(!keys(mapper.findDueScheduled(NOW, 500)).contains(eventKey));
        });
    }

    @Test
    @DisplayName("실패한 이벤트는 배치가 다시 집지 않고, 재처리로만 완료된다")
    void retriesFailedEventOnlyExplicitly() throws Exception {
        withRollback((mapper, connection) -> {
            Fixture fixture = givenHolding(connection);
            String eventKey = insertScheduled(connection, fixture, NOW.minusHours(1), "10000.00");
            long id = mapper.findByEventKey(eventKey).getPortfolioTransactionId();

            assertEquals(1, mapper.markFailed(id, "{\"error\":\"first\"}"));
            assertEquals(TransactionStatus.FAILED, mapper.findByEventKey(eventKey).getStatus());

            assertTrue(
                    !keys(mapper.findDueScheduled(NOW, 500)).contains(eventKey),
                    "실패분을 배치가 다시 집으면 매 배치마다 같은 실패를 반복합니다."
            );

            // 다시 실패하면 사유가 갱신되고, 재처리에 성공하면 완료로 넘어간다.
            assertEquals(1, mapper.markFailed(id, "{\"error\":\"second\"}"));
            assertEquals(1, mapper.markCompleted(id, NOW, "{\"result\":\"retried\"}"));
            assertEquals(TransactionStatus.COMPLETED, mapper.findByEventKey(eventKey).getStatus());

            // 완료된 것을 실패로 되돌리지는 않는다.
            assertEquals(0, mapper.markFailed(id, "{\"error\":\"late\"}"));
        });
    }

    @Test
    @DisplayName("해지하면 남은 예정분만 취소되고 이미 지급된 이력은 남는다")
    void cancelsOnlyScheduledEventsOfHolding() throws Exception {
        withRollback((mapper, connection) -> {
            Fixture fixture = givenHolding(connection);

            String paidKey = insertScheduled(connection, fixture, NOW.minusDays(1), "10000.00");
            String pendingKey = insertScheduled(connection, fixture, NOW.plusDays(1), "10000.00");

            mapper.markCompleted(
                    mapper.findByEventKey(paidKey).getPortfolioTransactionId(), NOW, "{}");

            assertEquals(1, mapper.cancelScheduledByHolding(fixture.holdingId));

            assertEquals(TransactionStatus.CANCELLED, mapper.findByEventKey(pendingKey).getStatus());
            assertEquals(TransactionStatus.COMPLETED, mapper.findByEventKey(paidKey).getStatus(),
                    "받은 이자를 지우면 현금과 이력이 어긋납니다.");
        });
    }

    @Test
    @DisplayName("초기화하면 그 세대의 예정 이벤트가 전부 취소된다")
    void cancelsAllScheduledEventsOfPortfolio() throws Exception {
        withRollback((mapper, connection) -> {
            Fixture fixture = givenHolding(connection);

            String first = insertScheduled(connection, fixture, NOW.plusDays(1), "10000.00");
            String second = insertScheduled(connection, fixture, NOW.plusDays(2), "10000.00");

            assertEquals(2, mapper.cancelScheduledByPortfolio(fixture.portfolioId));

            assertEquals(TransactionStatus.CANCELLED, mapper.findByEventKey(first).getStatus());
            assertEquals(TransactionStatus.CANCELLED, mapper.findByEventKey(second).getStatus());
        });
    }

    // ------------------------------------------------------------------ 준비

    /** 예·적금 상품 하나를 보유한 포트폴리오. */
    private static Fixture givenHolding(Connection connection) throws Exception {
        long userId = PortfolioFixtures.insertUser(connection);
        long portfolioId = PortfolioFixtures.insertPortfolio(connection, userId, 1, "20000000.00");
        long productId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");
        long holdingId = PortfolioFixtures.insertHolding(
                connection, portfolioId, productId, "1.000000", "10000000.00");

        return new Fixture(portfolioId, holdingId, productId);
    }

    private static String insertScheduled(
            Connection connection,
            Fixture fixture,
            LocalDateTime scheduledAt,
            String amount
    ) throws Exception {
        return insertScheduled(connection, fixture, scheduledAt, amount, TransactionType.INTEREST);
    }

    /**
     * 예정 이벤트 한 건. 서비스가 만들 모양 그대로 넣는다 —
     * 금액은 <b>미리 확정</b>돼 있고 {@code processed_at}은 비어 있다.
     */
    private static String insertScheduled(
            Connection connection,
            Fixture fixture,
            LocalDateTime scheduledAt,
            String amount,
            TransactionType type
    ) throws Exception {
        String eventKey = type.name().toLowerCase() + "-" + fixture.holdingId
                + "-" + UUID.randomUUID();

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO portfolio_transactions ("
                        + "portfolio_id, holding_id, product_id, transaction_type, amount,"
                        + " status, scheduled_at, event_key, idempotency_key, detail_json, created_at"
                        + ") VALUES (?, ?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, '{\"basis\":\"test\"}', NOW())",
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setLong(1, fixture.portfolioId);
            statement.setLong(2, fixture.holdingId);
            statement.setLong(3, fixture.productId);
            statement.setString(4, type.name());
            statement.setBigDecimal(5, new BigDecimal(amount));
            statement.setString(6, scheduledAt.toString());
            statement.setString(7, eventKey);
            statement.setString(8, eventKey);
            statement.executeUpdate();
        }

        return eventKey;
    }

    private static List<String> keys(List<PortfolioTransaction> events) {
        return events.stream().map(PortfolioTransaction::getEventKey).toList();
    }

    // ------------------------------------------------------------------ 실행 틀

    private interface Scenario {
        void run(PortfolioTransactionMapper mapper, Connection connection) throws Exception;
    }

    private static void withRollback(Scenario scenario) throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);

            org.springframework.transaction.TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                scenario.run(
                        context.getBean(PortfolioTransactionMapper.class),
                        DataSourceUtils.getConnection(dataSource)
                );
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("portfolioEventMapperJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }

    private static final class Fixture {

        private final long portfolioId;
        private final long holdingId;
        private final long productId;

        private Fixture(long portfolioId, long holdingId, long productId) {
            this.portfolioId = portfolioId;
            this.holdingId = holdingId;
            this.productId = productId;
        }
    }
}
