package org.firstfolio.persistence;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * {@code @Tag("jdbc")} 테스트가 실제 MySQL에 붙을 때 쓰는 접속 정보.
 *
 * <p>환경변수 → 시스템 프로퍼티 → {@code .env.local} 순으로 찾는다. 값은 커밋하지 않는다.</p>
 */
final class LocalDatabaseProperties {

    private static final Path LOCAL_ENV_FILE = Path.of(".env.local");

    private LocalDatabaseProperties() {
    }

    static Map<String, Object> load() throws IOException {
        Properties localProperties = new Properties();

        if (Files.isRegularFile(LOCAL_ENV_FILE)) {
            try (Reader reader = Files.newBufferedReader(LOCAL_ENV_FILE, StandardCharsets.UTF_8)) {
                localProperties.load(reader);
            }
        }

        Map<String, Object> properties = new HashMap<>();

        properties.put(
                "DB_DRIVER",
                resolve(localProperties, "DB_DRIVER", "net.sf.log4jdbc.sql.jdbcapi.DriverSpy")
        );
        properties.put("DB_URL", requireValue(localProperties, "DB_URL"));
        properties.put("DB_USERNAME", requireValue(localProperties, "DB_USERNAME"));
        properties.put("DB_PASSWORD", resolve(localProperties, "DB_PASSWORD", ""));

        return properties;
    }

    private static String requireValue(Properties localProperties, String key) {
        String value = resolve(localProperties, key, "");

        if (value.isBlank()) {
            throw new IllegalStateException(
                    key + "가 없습니다. 프로젝트 루트의 .env.local 또는 "
                            + "실행 환경변수에 값을 설정하세요."
            );
        }

        return value;
    }

    private static String resolve(Properties localProperties, String key, String defaultValue) {
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

            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
    }
}
