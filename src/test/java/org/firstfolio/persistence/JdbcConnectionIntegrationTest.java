package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("jdbc")
class JdbcConnectionIntegrationTest {

    private static final Path LOCAL_ENV_FILE = Path.of(".env.local");

    @Test
    @DisplayName("애플리케이션 DataSource로 MySQL에 연결하고 SELECT 1을 실행한다")
    void connectsToMySqlThroughApplicationDataSource() throws Exception {
        Map<String, Object> connectionProperties = loadConnectionProperties();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "jdbcConnectionIntegrationTest",
                            connectionProperties
                    )
            );
            context.register(RootConfig.class);
            context.refresh();

            DataSource dataSource = context.getBean(DataSource.class);

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertTrue(connection.isValid(5), "MySQL 연결이 유효해야 합니다.");
                assertTrue(resultSet.next(), "SELECT 1 결과가 존재해야 합니다.");
                assertEquals(1, resultSet.getInt(1));
            }
        }
    }

    private static Map<String, Object> loadConnectionProperties()
            throws IOException {
        Properties localProperties = new Properties();

        if (Files.isRegularFile(LOCAL_ENV_FILE)) {
            try (Reader reader = Files.newBufferedReader(
                    LOCAL_ENV_FILE,
                    StandardCharsets.UTF_8
            )) {
                localProperties.load(reader);
            }
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(
                "DB_DRIVER",
                resolve(
                        localProperties,
                        "DB_DRIVER",
                        "net.sf.log4jdbc.sql.jdbcapi.DriverSpy"
                )
        );
        properties.put(
                "DB_URL",
                requireValue(localProperties, "DB_URL")
        );
        properties.put(
                "DB_USERNAME",
                requireValue(localProperties, "DB_USERNAME")
        );
        properties.put(
                "DB_PASSWORD",
                resolve(localProperties, "DB_PASSWORD", "")
        );

        return properties;
    }

    private static String requireValue(
            Properties localProperties,
            String key
    ) {
        String value = resolve(localProperties, key, "");

        if (value.isBlank()) {
            throw new IllegalStateException(
                    key + "가 없습니다. 프로젝트 루트의 .env.local 또는 "
                            + "실행 환경변수에 값을 설정하세요."
            );
        }

        return value;
    }

    private static String resolve(
            Properties localProperties,
            String key,
            String defaultValue
    ) {
        String environmentValue = System.getenv(key);
        if (environmentValue != null) {
            return stripOptionalQuotes(environmentValue.trim());
        }

        String systemPropertyValue = System.getProperty(key);
        if (systemPropertyValue != null) {
            return stripOptionalQuotes(systemPropertyValue.trim());
        }

        String localValue = localProperties.getProperty(key);
        if (localValue != null) {
            return stripOptionalQuotes(localValue.trim());
        }

        return defaultValue;
    }

    private static String stripOptionalQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);

            if ((first == '"' && last == '"')
                    || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
    }
}
