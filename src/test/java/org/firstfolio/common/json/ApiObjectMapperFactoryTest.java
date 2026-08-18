package org.firstfolio.common.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiObjectMapperFactoryTest {

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    @Test
    @DisplayName("필드명을 snake_case로 직렬화한다")
    void serializesFieldNamesInSnakeCase() throws Exception {
        Sample sample = new Sample();
        sample.setPortfolioTransactionId(8201L);

        assertEquals(
                "8201",
                objectMapper.readTree(objectMapper.writeValueAsString(sample))
                        .get("portfolio_transaction_id")
                        .asText()
        );
    }

    @Test
    @DisplayName("snake_case 요청 필드를 역직렬화한다")
    void deserializesFieldNamesInSnakeCase() throws Exception {
        Sample sample = objectMapper.readValue(
                "{\"portfolio_transaction_id\":8201}",
                Sample.class
        );

        assertEquals(8201L, sample.getPortfolioTransactionId());
    }

    @Test
    @DisplayName("camelCase 요청 필드를 허용하지 않는다")
    void rejectsFieldNamesInCamelCase() {
        assertThrows(
                Exception.class,
                () -> objectMapper.readValue(
                        "{\"portfolioTransactionId\":8201}",
                        Sample.class
                )
        );
    }

    @Test
    @DisplayName("금액은 자릿수를 유지한 문자열로 직렬화한다")
    void serializesBigDecimalAsStringKeepingScale() throws Exception {
        Sample sample = new Sample();
        sample.setAmount(new BigDecimal("30000000.00"));

        String json = objectMapper.writeValueAsString(sample);

        // 숫자가 아니라 따옴표가 붙은 문자열이어야 한다.
        assertEquals(
                "\"30000000.00\"",
                objectMapper.readTree(json).get("amount").toString()
        );
    }

    @Test
    @DisplayName("@JsonFormat(NUMBER_FLOAT)을 붙인 필드는 숫자로 직렬화한다")
    void serializesAnnotatedDecimalAsNumber() throws Exception {
        Sample sample = new Sample();
        sample.setRatio(new BigDecimal("33.38"));

        assertEquals(
                "33.38",
                objectMapper.readTree(objectMapper.writeValueAsString(sample))
                        .get("ratio")
                        .toString()
        );
    }

    @Test
    @DisplayName("시각은 UTC 기준 Z 표기로 직렬화한다")
    void serializesDateTimeAsUtcWithZ() throws Exception {
        Sample sample = new Sample();
        sample.setProcessedAt(LocalDateTime.of(2026, 7, 29, 3, 0, 0));

        assertEquals(
                "2026-07-29T03:00:00Z",
                objectMapper.readTree(objectMapper.writeValueAsString(sample))
                        .get("processed_at")
                        .asText()
        );
    }

    @Test
    @DisplayName("Z가 붙은 시각을 그대로 UTC로 역직렬화한다")
    void deserializesZuluDateTime() throws Exception {
        Sample sample = objectMapper.readValue(
                "{\"processed_at\":\"2026-07-29T03:15:00Z\"}",
                Sample.class
        );

        assertEquals(LocalDateTime.of(2026, 7, 29, 3, 15, 0), sample.getProcessedAt());
    }

    @Test
    @DisplayName("오프셋이 붙은 시각은 UTC로 변환해 역직렬화한다")
    void convertsOffsetDateTimeToUtc() throws Exception {
        Sample sample = objectMapper.readValue(
                "{\"processed_at\":\"2026-07-29T12:15:00+09:00\"}",
                Sample.class
        );

        assertEquals(LocalDateTime.of(2026, 7, 29, 3, 15, 0), sample.getProcessedAt());
    }

    @Test
    @DisplayName("null 필드를 생략하지 않는다 (next_cursor, closed_at 등)")
    void keepsNullFields() throws Exception {
        String json = objectMapper.writeValueAsString(new Sample());

        assertNull(objectMapper.readTree(json).get("amount").textValue());
    }

    @Test
    @DisplayName("정의되지 않은 요청 필드는 거부한다")
    void rejectsUnknownFields() {
        assertThrows(
                Exception.class,
                () -> objectMapper.readValue("{\"unknown_field\":1}", Sample.class)
        );
    }

    static class Sample {

        private Long portfolioTransactionId;
        private BigDecimal amount;
        private LocalDateTime processedAt;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
        private BigDecimal ratio;

        public BigDecimal getRatio() {
            return ratio;
        }

        public void setRatio(BigDecimal ratio) {
            this.ratio = ratio;
        }

        public Long getPortfolioTransactionId() {
            return portfolioTransactionId;
        }

        public void setPortfolioTransactionId(Long portfolioTransactionId) {
            this.portfolioTransactionId = portfolioTransactionId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDateTime getProcessedAt() {
            return processedAt;
        }

        public void setProcessedAt(LocalDateTime processedAt) {
            this.processedAt = processedAt;
        }
    }
}
