package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.policy.mapper.SystemPolicyMapper;
import org.firstfolio.policy.service.SystemPolicyReader;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 정책 조회를 실제 MySQL에 붙여서 확인한다 (#64).
 *
 * <h3>왜 필요한가</h3>
 *
 * <p>활성 버전을 고르는 조건이 <b>넷</b>이다 — {@code is_active} · {@code effective_from} ·
 * {@code effective_to}(null 허용) · 최신 {@code version_no}.
 * <b>하나만 어긋나도 옛 정책으로 조용히 계산된다.</b> 값이 틀린 것이 아니라 다른 버전이 나오는
 * 것이라 예외도 안 난다. 서비스 테스트는 매퍼를 모킹해 이 SQL을 한 줄도 읽지 않는다.</p>
 *
 * <h3>⚠️ 한 테스트 안에서 같은 파라미터로 두 번 묻지 않는다</h3>
 *
 * <p>조건별로 테스트를 쪼갠 것은 취향이 아니라 <b>MyBatis 1차 캐시 때문</b>이다.
 * 트랜잭션 안에서는 {@code SqlSession}이 재사용되고 <b>같은 문장·같은 파라미터의 결과가
 * 캐시된다.</b> 데이터를 raw JDBC로 넣으면 MyBatis는 그 사실을 몰라 캐시를 비우지 않는다.</p>
 *
 * <pre>
 * findActive(key, NOW)  → null (캐시됨)
 * INSERT (raw JDBC)     → MyBatis는 모른다
 * findActive(key, NOW)  → 캐시 적중, 여전히 null   ← 실제로 겪었다
 * </pre>
 *
 * <p>그래서 <b>데이터를 먼저 다 넣고 마지막에 한 번만 묻는다.</b></p>
 *
 * <p>넣은 데이터는 <b>전부 롤백</b>한다.</p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class SystemPolicyJdbcTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 6, 0);

    /**
     * 기존 시드 정책과 섞이지 않도록 테스트 전용 키를 쓴다.
     *
     * <p><b>테스트마다 새로 만든다.</b> 클래스 상수로 두면 롤백이 실패했을 때 다음 테스트가
     * 유니크 제약에 걸려 원인이 엉뚱한 곳으로 보인다.</p>
     */
    private final String testKey = "JDBC_TEST_" + UUID.randomUUID().toString().substring(0, 8);

    // ------------------------------------------------------------- 버전 선택 네 조건

    @Test
    @DisplayName("여러 버전이 유효하면 가장 높은 버전을 고른다")
    void picksHighestValidVersion() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.001"), NOW.minusDays(10), null, true);
            insert(connection, userId, 2, rate("0.002"), NOW.minusDays(5), null, true);

            assertEquals(2, mapper(context).findActive(testKey, NOW).getVersionNo());
        });
    }

    @Test
    @DisplayName("is_active=false는 버전이 높아도 제외한다")
    void excludesInactiveVersion() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.001"), NOW.minusDays(10), null, true);
            insert(connection, userId, 2, rate("0.002"), NOW.minusDays(5), null, false);

            assertEquals(1, mapper(context).findActive(testKey, NOW).getVersionNo());
        });
    }

    @Test
    @DisplayName("effective_from이 아직 안 됐으면 제외한다")
    void excludesNotYetEffectiveVersion() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.001"), NOW.minusDays(10), null, true);
            insert(connection, userId, 2, rate("0.002"), NOW.plusDays(1), null, true);

            assertEquals(1, mapper(context).findActive(testKey, NOW).getVersionNo());
        });
    }

    @Test
    @DisplayName("effective_to가 지났으면 제외한다")
    void excludesExpiredVersion() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.001"), NOW.minusDays(10), null, true);
            insert(connection, userId, 2, rate("0.002"), NOW.minusDays(9), NOW.minusDays(2), true);

            assertEquals(1, mapper(context).findActive(testKey, NOW).getVersionNo());
        });
    }

    @Test
    @DisplayName("effective_to가 null이면 아직 끝나지 않은 정책이다")
    void treatsNullEffectiveToAsOpenEnded() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.001"), NOW.minusYears(1), null, true);

            // IS NULL 조건을 빠뜨리면 여기서 null이 나온다.
            assertNotNull(mapper(context).findActive(testKey, NOW.plusYears(5)));
        });
    }

    // ------------------------------------------------------------- 경계·격리

    @Test
    @DisplayName("경계 — effective_from 정각은 포함, effective_to 정각은 제외")
    void handlesBoundaries() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);
            LocalDateTime from = NOW;
            LocalDateTime to = NOW.plusDays(1);

            insert(connection, userId, 1, rate("0.001"), from, to, true);

            SystemPolicyMapper mapper = mapper(context);

            // 네 번 묻지만 파라미터가 모두 달라 1차 캐시에 걸리지 않는다.
            assertNotNull(mapper.findActive(testKey, from), "시작 정각은 유효");
            assertNull(mapper.findActive(testKey, from.minusSeconds(1)), "시작 1초 전은 무효");
            assertNotNull(mapper.findActive(testKey, to.minusSeconds(1)), "종료 1초 전은 유효");
            assertNull(mapper.findActive(testKey, to), "종료 정각은 무효");
        });
    }

    @Test
    @DisplayName("다른 키의 정책은 섞이지 않는다")
    void isolatesByPolicyKey() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.001"), NOW.minusDays(1), null, true);

            assertNull(mapper(context).findActive(testKey + "_OTHER", NOW));
        });
    }

    @Test
    @DisplayName("정책이 없으면 null이다 — 오류가 아니다")
    void returnsNullWhenNoPolicy() throws Exception {
        withRollback((connection, context) ->
                assertNull(mapper(context).findActive(testKey, NOW)));
    }

    // ------------------------------------------------------------- Reader

    @Test
    @DisplayName("SystemPolicyReader가 config_json을 JSON으로 돌려준다")
    void readerParsesConfigJson() throws Exception {
        withRollback((connection, context) -> {
            long userId = PortfolioFixtures.insertUser(connection);

            insert(connection, userId, 1, rate("0.00015"), NOW.minusDays(1), null, true);

            assertEquals(
                    "0.00015",
                    context.getBean(SystemPolicyReader.class)
                            .findActiveConfig(testKey, NOW)
                            .get("buy_fee_rate")
                            .asText()
            );
        });
    }

    @Test
    @DisplayName("정책이 없으면 Reader도 null을 준다 — 호출한 쪽이 기본값으로 넘어간다")
    void readerReturnsNullWhenNoPolicy() throws Exception {
        withRollback((connection, context) ->
                assertNull(context.getBean(SystemPolicyReader.class).findActiveConfig(testKey, NOW)));
    }

    // ------------------------------------------------------------- 도구

    /** 시나리오를 트랜잭션 안에서 돌리고 반드시 롤백한다. */
    private void withRollback(Scenario scenario) throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                // 트랜잭션에 묶인 커넥션이어야 롤백된다. dataSource.getConnection()은 별개 커넥션이다.
                scenario.run(DataSourceUtils.getConnection(dataSource), context);
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @FunctionalInterface
    private interface Scenario {
        void run(Connection connection, AnnotationConfigApplicationContext context) throws Exception;
    }

    private static SystemPolicyMapper mapper(AnnotationConfigApplicationContext context) {
        return context.getBean(SystemPolicyMapper.class);
    }

    private static String rate(String buyFeeRate) {
        return "{\"buy_fee_rate\": \"" + buyFeeRate + "\"}";
    }

    /** 시각은 UTC로 넣는다. MySQL의 {@code NOW()}는 KST라 9시간 어긋난다. */
    private void insert(
            Connection connection,
            long userId,
            int versionNo,
            String configJson,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo,
            boolean active
    ) throws Exception {
        String sql = "INSERT INTO system_policies ("
                + "policy_key, version_no, config_json, effective_from, effective_to,"
                + " is_active, created_by, created_at"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP())";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, testKey);
            statement.setInt(2, versionNo);
            statement.setString(3, configJson);
            statement.setObject(4, effectiveFrom);
            statement.setObject(5, effectiveTo);
            statement.setBoolean(6, active);
            statement.setLong(7, userId);
            statement.executeUpdate();
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("systemPolicyJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }
}
