package org.firstfolio.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * {@code @Tag("jdbc")} 테스트가 쓰는 최소 데이터.
 *
 * <p>모두 호출한 쪽의 트랜잭션에 참여하므로 <b>롤백하면 함께 사라진다.</b></p>
 */
final class PortfolioFixtures {

    private PortfolioFixtures() {
    }

    /**
     * 테스트용 사용자 한 명.
     *
     * <p>{@code users}는 인증 도메인 소유라 우리 범위 밖에서 바뀐다. 실제로
     * {@code db-init/001_schema.sql}의 {@code auth_provider}/{@code auth_subject}가 로컬 DB에서는
     * {@code firebase_uid} 하나로 바뀌어 있었다. 어느 쪽이든 돌도록 컬럼을 확인하고 맞춰 넣는다.</p>
     */
    static long insertUser(Connection connection) throws Exception {
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

    /** 활성 포트폴리오 한 세대. */
    static long insertPortfolio(
            Connection connection,
            long userId,
            int generationNo,
            String cashBalance
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO portfolios ("
                        + "user_id, generation_no, status, initial_amount, cash_balance,"
                        + " opened_at, created_at, updated_at"
                        + ") VALUES (?, ?, 'ACTIVE', 30000000.00, ?, NOW(), NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setLong(1, userId);
            statement.setInt(2, generationNo);
            statement.setBigDecimal(3, new BigDecimal(cashBalance));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();

                return keys.getLong(1);
            }
        }
    }

    static void insertHolding(
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

    /** 공개된 상품 하나. 없으면 시드가 안 들어간 것이다. */
    static long activeProductId(Connection connection, String assetType) throws Exception {
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
}
