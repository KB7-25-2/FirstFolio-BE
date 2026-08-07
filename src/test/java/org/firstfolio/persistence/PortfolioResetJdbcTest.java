package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.service.PortfolioQueryService;
import org.firstfolio.portfolio.service.PortfolioResetResult;
import org.firstfolio.portfolio.service.PortfolioResetService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 포트폴리오 초기화를 실제 MySQL에 붙여서 확인한다 (FUNC-037).
 *
 * <p>세대 종료·새 세대 생성·이력 기록이 <b>세 테이블에 걸쳐</b> 일어난다. 서비스 테스트는
 * 매퍼를 모킹하므로 유니크 제약({@code uq_portfolios_user_generation})이 실제로 지키는지,
 * 닫힌 세대의 보유가 새 세대 조회에서 정말 빠지는지는 여기서만 드러난다.</p>
 *
 * <p>넣은 데이터는 <b>테스트 끝에 전부 롤백</b>한다.</p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class PortfolioResetJdbcTest {

    @Test
    @DisplayName("실제 DB에서 세대를 닫고 새 세대를 열며, 닫힌 세대의 보유는 조회에서 빠진다")
    void resetsGenerationOnRealDatabase() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            PortfolioResetService resetService = context.getBean(PortfolioResetService.class);
            PortfolioQueryService queryService = context.getBean(PortfolioQueryService.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                Connection connection = DataSourceUtils.getConnection(dataSource);

                long userId = PortfolioFixtures.insertUser(connection);
                long oldPortfolioId =
                        PortfolioFixtures.insertPortfolio(connection, userId, 1, "1250000.00");
                long depositId = PortfolioFixtures.activeProductId(connection, "DEPOSIT_SAVINGS");
                long stockId = PortfolioFixtures.activeProductId(connection, "STOCK");

                PortfolioFixtures.insertHolding(connection, oldPortfolioId, depositId, "1.000000", "10000000.00");
                PortfolioFixtures.insertHolding(connection, oldPortfolioId, stockId, "100.000000", "18000000.00");

                // 초기화 전 — 보유 2건이 보인다.
                assertEquals(2, queryService.findCurrent(userId).getHoldings().size());

                PortfolioResetResult result = resetService.reset(
                        userId,
                        PortfolioResetService.RESET_CONFIRMATION,
                        "reset-jdbc-1"
                );

                assertEquals(oldPortfolioId, result.getClosedPortfolioId());
                assertEquals(2, result.getGenerationNo());
                assertEquals(0, new BigDecimal("30000000.00").compareTo(result.getCashBalance()));
                assertNotNull(result.getResetTransactionId());

                // 이전 세대는 CLOSED이고 종료 시각이 찍혔다.
                assertEquals("CLOSED", portfolioStatus(connection, oldPortfolioId));
                assertNotNull(closedAt(connection, oldPortfolioId), "closed_at이 찍혀야 합니다.");

                // 보유 행은 그대로 ACTIVE다 — 판 적이 없으므로 상태를 바꾸지 않는다.
                assertEquals(2, activeHoldingCount(connection, oldPortfolioId),
                        "닫힌 세대의 보유는 상태를 유지해야 합니다.");

                // 그런데도 새 세대 조회에는 안 나온다 — 세대가 갈라져 있기 때문이다.
                PortfolioDetailResponse current = queryService.findCurrent(userId);

                assertEquals(result.getNewPortfolioId(), current.getPortfolioId());
                assertEquals(2, current.getGenerationNo());
                assertTrue(current.getHoldings().isEmpty(), "새 세대는 보유가 없어야 합니다.");
                assertEquals(new BigDecimal("30000000.00"), current.getCashBalance());
                assertEquals(new BigDecimal("30000000.00"), current.getSummary().getTotalAssets());
                assertEquals(new BigDecimal("0.00"), current.getSummary().getProfitLoss());

                // 새 세대 이력의 첫 줄이 RESET이다.
                PortfolioTransactionPageResponse history =
                        queryService.findCurrentTransactions(userId, null, null, null);

                assertEquals(1, history.getItems().size());
                assertEquals("RESET", history.getItems().get(0).getTransactionType());
                assertEquals(
                        result.getResetTransactionId(),
                        history.getItems().get(0).getPortfolioTransactionId()
                );

                // 초기화 직전 상태가 이력에 남아 있다.
                assertEquals(
                        oldPortfolioId,
                        history.getItems().get(0).getDetail().get("previous_portfolio_id").asLong()
                );
                assertEquals(
                        2,
                        history.getItems().get(0).getDetail().get("previous_holding_count").asInt()
                );
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @Test
    @DisplayName("같은 키로 다시 부르면 세대가 또 생기지 않는다")
    void staysIdempotentOnRealDatabase() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            PortfolioResetService resetService = context.getBean(PortfolioResetService.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                Connection connection = DataSourceUtils.getConnection(dataSource);

                long userId = PortfolioFixtures.insertUser(connection);
                PortfolioFixtures.insertPortfolio(connection, userId, 1, "1250000.00");

                PortfolioResetResult first = resetService.reset(
                        userId, PortfolioResetService.RESET_CONFIRMATION, "reset-jdbc-2");
                PortfolioResetResult again = resetService.reset(
                        userId, PortfolioResetService.RESET_CONFIRMATION, "reset-jdbc-2");

                assertEquals(first.getNewPortfolioId(), again.getNewPortfolioId());
                assertEquals(first.getClosedPortfolioId(), again.getClosedPortfolioId());
                assertEquals(first.getResetTransactionId(), again.getResetTransactionId());
                assertEquals(2, generationCount(connection, userId), "세대가 둘이어야 합니다.");
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("portfolioResetJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }

    private static String portfolioStatus(Connection connection, long portfolioId) throws Exception {
        return singleValue(connection,
                "SELECT status FROM portfolios WHERE portfolio_id = ?", portfolioId);
    }

    private static String closedAt(Connection connection, long portfolioId) throws Exception {
        return singleValue(connection,
                "SELECT closed_at FROM portfolios WHERE portfolio_id = ?", portfolioId);
    }

    private static int activeHoldingCount(Connection connection, long portfolioId) throws Exception {
        return Integer.parseInt(singleValue(connection,
                "SELECT COUNT(*) FROM portfolio_holdings"
                        + " WHERE portfolio_id = ? AND status = 'ACTIVE'", portfolioId));
    }

    private static int generationCount(Connection connection, long userId) throws Exception {
        return Integer.parseInt(singleValue(connection,
                "SELECT COUNT(*) FROM portfolios WHERE user_id = ?", userId));
    }

    private static String singleValue(Connection connection, String sql, long parameter)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parameter);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

                return resultSet.getString(1);
            }
        }
    }
}
