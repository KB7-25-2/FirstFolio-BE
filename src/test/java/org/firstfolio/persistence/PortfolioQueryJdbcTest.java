package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.service.InitialGrantService;
import org.firstfolio.portfolio.service.PortfolioQueryService;
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
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 포트폴리오 조회를 실제 MySQL에 붙여서 확인한다 (FUNC-029/034/036).
 *
 * <p>서비스 테스트는 매퍼를 모킹하므로 <b>SQL을 한 줄도 실행하지 않는다.</b> 컬럼명 오타,
 * enum 매핑, JOIN 결과처럼 실행해야만 드러나는 것들을 여기서 잡는다.</p>
 *
 * <p>넣은 데이터는 <b>테스트 끝에 전부 롤백</b>한다. 로컬 DB의 상품 시드는 읽기만 한다.</p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class PortfolioQueryJdbcTest {

    /** 이 시각의 가격을 넣어 두면 기존 시세와 무관하게 "가장 최근 가격"이 된다. */
    private static final String FUTURE_PRICE_TIME = "2099-01-01 00:00:00";

    @Test
    @DisplayName("실제 DB에서 현금·보유·평가액·비중·이력을 조회한다")
    void queriesPortfolioFromRealDatabase() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            InitialGrantService grantService = context.getBean(InitialGrantService.class);
            PortfolioQueryService queryService = context.getBean(PortfolioQueryService.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                Connection connection = DataSourceUtils.getConnection(dataSource);

                long userId = insertUser(connection);
                long depositProductId = activeProductId(connection, "DEPOSIT_SAVINGS");
                long stockProductId = activeProductId(connection, "STOCK");

                // 기초 과정 완료 지급 (FUNC-029). 아직 API가 없어 이 경로가 유일한 검증 수단이다.
                Long portfolioId = grantService.grantOnFoundationCompleted(userId, 1L).getPortfolioId();

                assertNotNull(portfolioId, "지급이 포트폴리오를 만들어야 합니다.");

                // 상품을 사고 남은 현금 상태를 만든다.
                updateCashBalance(connection, portfolioId, "2000000.00");
                insertHolding(connection, portfolioId, depositProductId, "1.000000", "10000000.00");
                insertHolding(connection, portfolioId, stockProductId, "100.000000", "18000000.00");
                insertPrice(connection, stockProductId, "182000.0000");

                PortfolioDetailResponse detail = queryService.findCurrent(userId);

                assertEquals(portfolioId, detail.getPortfolioId());
                assertEquals(1, detail.getGenerationNo());
                assertEquals(new BigDecimal("2000000.00"), detail.getCashBalance());

                assertEquals(new BigDecimal("28200000.00"), detail.getSummary().getHoldingsValue());
                assertEquals(new BigDecimal("30200000.00"), detail.getSummary().getTotalAssets());
                assertEquals(new BigDecimal("200000.00"), detail.getSummary().getProfitLoss());

                assertEquals(2, detail.getHoldings().size());

                PortfolioDetailResponse.Holding deposit = holdingOf(detail, depositProductId);
                PortfolioDetailResponse.Holding stock = holdingOf(detail, stockProductId);

                assertEquals("PRINCIPAL", deposit.getValuationBasis());
                assertEquals(new BigDecimal("10000000.00"), deposit.getValuationAmount());
                assertEquals("DEPOSIT_SAVINGS", deposit.getAssetType(), "자산군 enum이 매핑돼야 합니다.");

                assertEquals("MARKET_PRICE", stock.getValuationBasis());
                assertEquals(new BigDecimal("18200000.00"), stock.getValuationAmount());
                assertNotNull(stock.getValuedAt(), "시세 기준 시점이 있어야 합니다.");

                // 사용자에게 나가는 이름은 가명이다 (FUNC-032).
                assertEquals(displayName(connection, stockProductId), stock.getDisplayName());
                assertFalse(
                        stock.getDisplayName().equals(sourceProductName(connection, stockProductId)),
                        "원상품명이 그대로 나가면 안 됩니다."
                );

                assertEquals(2, detail.getAllocation().size());
                // 분모는 총자산이라 합이 100%가 아니다. 나머지 6.62%가 현금이다.
                assertEquals("DEPOSIT_SAVINGS", detail.getAllocation().get(0).getAssetType());
                assertEquals(new BigDecimal("33.11"), detail.getAllocation().get(0).getRatio());
                assertEquals("STOCK", detail.getAllocation().get(1).getAssetType());
                assertEquals(new BigDecimal("60.26"), detail.getAllocation().get(1).getRatio());

                PortfolioTransactionPageResponse history =
                        queryService.findCurrentTransactions(userId, null, null, null);

                assertEquals(1, history.getItems().size());
                assertEquals("INITIAL_GRANT", history.getItems().get(0).getTransactionType());
                assertEquals(
                        new BigDecimal("30000000.00"),
                        history.getItems().get(0).getAmount()
                );

                // 두 방향을 모두 본다. 빈 결과만 확인하면 유형 바인딩이 깨져도 통과한다.
                assertEquals(
                        1,
                        queryService.findCurrentTransactions(userId, "INITIAL_GRANT", null, null)
                                .getItems().size(),
                        "일치하는 유형은 걸러지지 않아야 합니다."
                );
                assertTrue(
                        queryService.findCurrentTransactions(userId, "BUY", null, null)
                                .getItems().isEmpty(),
                        "다른 유형은 걸러져야 합니다."
                );
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @Test
    @DisplayName("포트폴리오가 없는 사용자는 404다")
    void returnsNotFoundWithoutPortfolio() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            PortfolioQueryService queryService = context.getBean(PortfolioQueryService.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                long userId = insertUser(DataSourceUtils.getConnection(dataSource));

                ApiException exception =
                        assertThrows(ApiException.class, () -> queryService.findCurrent(userId));

                assertEquals(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND, exception.getErrorCode());
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("portfolioQueryJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }

    private static PortfolioDetailResponse.Holding holdingOf(
            PortfolioDetailResponse detail,
            long productId
    ) {
        for (PortfolioDetailResponse.Holding holding : detail.getHoldings()) {
            if (holding.getProductId() == productId) {
                return holding;
            }
        }

        throw new AssertionError("상품 " + productId + " 보유가 응답에 없습니다.");
    }

    /**
     * 테스트용 사용자 한 명.
     *
     * <p>{@code users}는 인증 도메인 소유라 우리 범위 밖에서 바뀐다. 실제로
     * {@code db-init/001_schema.sql}의 {@code auth_provider}/{@code auth_subject}가
     * 로컬 DB에서는 {@code firebase_uid} 하나로 바뀌어 있었다. 어느 쪽이든 돌도록
     * 컬럼을 확인하고 맞춰 넣는다 — 포트폴리오 조회 검증이 남의 도메인 변경에
     * 발목 잡힐 이유가 없다.</p>
     */
    private static long insertUser(Connection connection) throws Exception {
        String subject = "jdbc-test-" + UUID.randomUUID();
        String nickname = "테스트" + subject.substring(subject.length() - 12);

        String sql = hasColumn(connection, "users", "firebase_uid")
                ? "INSERT INTO users ("
                + "firebase_uid, nickname, role_code, status,"
                + " point_balance, newsletter_opt_in, created_at, updated_at"
                + ") VALUES (?, ?, 'USER', 'ACTIVE', 0, FALSE, NOW(), NOW())"
                : "INSERT INTO users ("
                + "auth_provider, auth_subject, nickname, role_code, status,"
                + " point_balance, newsletter_opt_in, created_at, updated_at"
                + ") VALUES ('LOCAL', ?, ?, 'USER', 'ACTIVE', 0, FALSE, NOW(), NOW())";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, subject);
            statement.setString(2, nickname);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();

                return keys.getLong(1);
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns"
                        + " WHERE table_schema = DATABASE()"
                        + "   AND table_name = ? AND column_name = ?"
        )) {
            statement.setString(1, table);
            statement.setString(2, column);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long activeProductId(Connection connection, String assetType) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id FROM financial_products"
                        + " WHERE is_active = TRUE AND asset_type = ?"
                        + " ORDER BY product_id LIMIT 1"
        )) {
            statement.setString(1, assetType);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError(
                            "공개된 " + assetType + " 상품이 없습니다. 상품 시드를 먼저 등록하세요."
                    );
                }

                return resultSet.getLong(1);
            }
        }
    }

    private static String displayName(Connection connection, long productId) throws Exception {
        return productColumn(connection, productId, "display_name");
    }

    private static String sourceProductName(Connection connection, long productId) throws Exception {
        return productColumn(connection, productId, "source_product_name");
    }

    private static String productColumn(Connection connection, long productId, String column)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM financial_products WHERE product_id = ?"
        )) {
            statement.setLong(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

                return resultSet.getString(1);
            }
        }
    }

    private static void updateCashBalance(Connection connection, long portfolioId, String cash)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE portfolios SET cash_balance = ? WHERE portfolio_id = ?"
        )) {
            statement.setBigDecimal(1, new BigDecimal(cash));
            statement.setLong(2, portfolioId);
            statement.executeUpdate();
        }
    }

    private static void insertHolding(
            Connection connection,
            long portfolioId,
            long productId,
            String quantity,
            String principal
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO portfolio_holdings ("
                        + "portfolio_id, product_id, quantity, principal_amount,"
                        + " terms_snapshot_json, status, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, '{}', 'ACTIVE', NOW(), NOW())"
        )) {
            statement.setLong(1, portfolioId);
            statement.setLong(2, productId);
            statement.setBigDecimal(3, new BigDecimal(quantity));
            statement.setBigDecimal(4, new BigDecimal(principal));
            statement.executeUpdate();
        }
    }

    private static void insertPrice(Connection connection, long productId, String price)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO product_prices ("
                        + "product_id, price, reference_at, source_type, generation_key, created_at"
                        + ") VALUES (?, ?, ?, 'SIMULATION', ?, NOW())"
        )) {
            statement.setLong(1, productId);
            statement.setBigDecimal(2, new BigDecimal(price));
            statement.setString(3, FUTURE_PRICE_TIME);
            statement.setString(4, "jdbc-test-" + UUID.randomUUID());
            statement.executeUpdate();
        }
    }
}
