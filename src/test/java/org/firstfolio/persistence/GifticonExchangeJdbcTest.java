package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.dto.request.GifticonExchangeRequest;
import org.firstfolio.gifticon.dto.response.GifticonExchangeResponse;
import org.firstfolio.gifticon.service.GifticonExchangeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("jdbc")
class GifticonExchangeJdbcTest {

    @Test
    void concurrentSameRequestDeductsAndAssignsExactlyOnce() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            Fixture fixture = fixture(dataSource, 1, 1);
            try {
                GifticonExchangeService service = context.getBean(GifticonExchangeService.class);
                List<Attempt> attempts = race(List.of(
                        () -> service.exchange(
                                fixture.userIds.get(0), "same-request",
                                new GifticonExchangeRequest(fixture.productId)
                        ),
                        () -> service.exchange(
                                fixture.userIds.get(0), "same-request",
                                new GifticonExchangeRequest(fixture.productId)
                        )
                ));

                assertEquals(2, attempts.stream().filter(Attempt::succeeded).count());
                assertEquals(1, attempts.stream()
                        .filter(attempt -> attempt.response.idempotentReplay()).count());
                assertEquals(1, count(dataSource,
                        "SELECT COUNT(*) FROM gifticon_orders WHERE gifticon_product_id = ?",
                        fixture.productId));
                assertEquals(1, count(dataSource,
                        "SELECT COUNT(*) FROM gifticon_codes"
                                + " WHERE gifticon_product_id = ? AND status = 'ASSIGNED'",
                        fixture.productId));
                assertEquals(5000, balance(dataSource, fixture.userIds.get(0)));
            } finally {
                cleanup(dataSource, fixture);
            }
        }
    }

    @Test
    void concurrentUsersCannotOversellLastCode() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSource dataSource = context.getBean(DataSource.class);
            Fixture fixture = fixture(dataSource, 2, 1);
            try {
                GifticonExchangeService service = context.getBean(GifticonExchangeService.class);
                List<Attempt> attempts = race(List.of(
                        () -> service.exchange(
                                fixture.userIds.get(0), "user-one",
                                new GifticonExchangeRequest(fixture.productId)
                        ),
                        () -> service.exchange(
                                fixture.userIds.get(1), "user-two",
                                new GifticonExchangeRequest(fixture.productId)
                        )
                ));

                assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
                assertEquals(1, attempts.stream()
                        .filter(attempt -> attempt.errorCode == ErrorCode.GIFTICON_SOLD_OUT)
                        .count());
                assertEquals(1, count(dataSource,
                        "SELECT COUNT(*) FROM gifticon_orders WHERE gifticon_product_id = ?",
                        fixture.productId));
                assertEquals(15000, fixture.userIds.stream()
                        .mapToInt(userId -> balance(dataSource, userId)).sum());
            } finally {
                cleanup(dataSource, fixture);
            }
        }
    }

    private static List<Attempt> race(
            List<Callable<GifticonExchangeResponse>> requests
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Attempt>> futures = new ArrayList<>();
            for (Callable<GifticonExchangeResponse> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return Attempt.success(request.call());
                    } catch (ApiException exception) {
                        return Attempt.failure(exception.getErrorCode());
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Attempt> results = new ArrayList<>();
            for (Future<Attempt> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Fixture fixture(
            DataSource dataSource,
            int userCount,
            int codeCount
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            List<Long> userIds = new ArrayList<>();
            for (int index = 0; index < userCount; index++) {
                userIds.add(insertUser(connection));
            }
            long productId = insertProduct(connection);
            for (int index = 0; index < codeCount; index++) {
                insertCode(connection, productId, index);
            }
            return new Fixture(userIds, productId);
        }
    }

    private static long insertUser(Connection connection) throws Exception {
        String identity = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (firebase_uid, nickname, role_code, status, point_balance,"
                        + " newsletter_opt_in, created_at, updated_at)"
                        + " VALUES (?, ?, 'USER', 'ACTIVE', 10000, FALSE, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, "gifticon-jdbc-" + identity);
            statement.setString(2, "기프티콘" + identity.substring(0, 8));
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private static long insertProduct(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gifticon_products (name, brand_name, category, face_value_krw,"
                        + " required_points, status, created_at, updated_at)"
                        + " VALUES ('동시성 상품', '테스트', 'TEST', 5000, 5000, 'ON_SALE',"
                        + " UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private static void insertCode(Connection connection, long productId, int sequence)
            throws Exception {
        byte[] fingerprint = new byte[32];
        fingerprint[0] = (byte) (sequence + 1);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gifticon_codes (gifticon_product_id, code_ciphertext, code_masked,"
                        + " code_fingerprint, encryption_key_version, expires_at, status, created_at)"
                        + " VALUES (?, ?, '********9012', ?, 'v1', ?, 'AVAILABLE', UTC_TIMESTAMP())"
        )) {
            statement.setLong(1, productId);
            statement.setBytes(2, new byte[]{1, 2, 3});
            statement.setBytes(3, fingerprint);
            statement.setObject(4, LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
            statement.executeUpdate();
        }
    }

    private static long generatedId(PreparedStatement statement) throws Exception {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertTrue(keys.next());
            return keys.getLong(1);
        }
    }

    private static int balance(DataSource dataSource, long userId) {
        try {
            return count(dataSource, "SELECT point_balance FROM users WHERE user_id = ?", userId);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int count(DataSource dataSource, String sql, long value) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private static void cleanup(DataSource dataSource, Fixture fixture) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            List<Long> pointTransactionIds = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT point_transaction_id FROM gifticon_orders"
                            + " WHERE gifticon_product_id = ?"
            )) {
                statement.setLong(1, fixture.productId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) pointTransactionIds.add(resultSet.getLong(1));
                }
            }
            execute(connection, "DELETE FROM gifticon_orders WHERE gifticon_product_id = ?",
                    fixture.productId);
            for (long pointTransactionId : pointTransactionIds) {
                execute(connection, "DELETE FROM point_transactions WHERE point_transaction_id = ?",
                        pointTransactionId);
            }
            execute(connection, "DELETE FROM gifticon_codes WHERE gifticon_product_id = ?",
                    fixture.productId);
            execute(connection, "DELETE FROM gifticon_products WHERE gifticon_product_id = ?",
                    fixture.productId);
            for (long userId : fixture.userIds) {
                execute(connection, "DELETE FROM users WHERE user_id = ?", userId);
            }
        }
    }

    private static void execute(Connection connection, String sql, long value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            statement.executeUpdate();
        }
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("gifticonExchangeJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();
        return context;
    }

    private record Fixture(List<Long> userIds, long productId) { }

    private static final class Attempt {
        private final GifticonExchangeResponse response;
        private final ErrorCode errorCode;

        private Attempt(GifticonExchangeResponse response, ErrorCode errorCode) {
            this.response = response;
            this.errorCode = errorCode;
        }

        private static Attempt success(GifticonExchangeResponse response) {
            assertNotNull(response);
            return new Attempt(response, null);
        }

        private static Attempt failure(ErrorCode errorCode) {
            return new Attempt(null, errorCode);
        }

        private boolean succeeded() {
            return response != null;
        }
    }
}
