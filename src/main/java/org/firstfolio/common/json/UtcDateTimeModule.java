package org.firstfolio.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * DB의 DATETIME 컬럼에는 UTC 값을 저장한다(PROJECT_SPEC 16장, 스키마 주석).
 * 따라서 {@link LocalDateTime}은 항상 UTC 기준 시각으로 취급하고,
 * API 표현은 {@code 2026-07-29T03:00:00Z} 형태로 통일한다.
 */
public final class UtcDateTimeModule extends SimpleModule {

    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");

    public UtcDateTimeModule() {
        addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
    }

    private static final class UtcLocalDateTimeSerializer
            extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(
                LocalDateTime value,
                JsonGenerator generator,
                SerializerProvider provider
        ) throws IOException {
            generator.writeString(UTC_FORMATTER.format(value));
        }
    }

    /**
     * {@code Z} 또는 오프셋이 붙은 값은 UTC로 변환해 받고,
     * 오프셋이 없는 값은 이미 UTC로 간주한다.
     */
    private static final class UtcLocalDateTimeDeserializer
            extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(
                JsonParser parser,
                DeserializationContext context
        ) throws IOException {
            String text = parser.getText();

            if (text == null || text.isBlank()) {
                return null;
            }

            String value = text.trim();

            try {
                // OffsetDateTime은 'Z'와 '+09:00' 형태를 모두 받는다.
                return OffsetDateTime.parse(value)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 오프셋이 없는 표현으로 재시도한다.
            }

            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException exception) {
                throw new IOException(
                        "날짜·시각 형식이 올바르지 않습니다: " + value,
                        exception
                );
            }
        }
    }
}
