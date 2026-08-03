package org.firstfolio.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;

/**
 * API_DOCS.md의 요청·응답 표현에 맞춘 ObjectMapper를 만든다.
 *
 * <ul>
 *   <li>필드명은 snake_case ({@code portfolio_id}, {@code cash_balance}).</li>
 *   <li>금액·수량·단가 등 {@link BigDecimal}은 자릿수 손실을 막기 위해 문자열로 표현한다
 *       ({@code "30000000.00"}). 비율처럼 숫자로 표현해야 하는 소수의 필드는
 *       해당 DTO 필드에 {@code @JsonFormat(shape = NUMBER_FLOAT)}을 직접 붙인다
 *       (예: {@code allocation[].ratio}).</li>
 *   <li>시각은 UTC 기준 {@code 2026-07-29T03:00:00Z} 형태 ({@link UtcDateTimeModule}).</li>
 *   <li>{@code next_cursor}, {@code closed_at}처럼 명세가 {@code null}을 명시하는 필드가 있어
 *       null 필드를 생략하지 않는다.</li>
 *   <li>정의되지 않은 요청 필드는 조용히 무시하지 않고 오류로 처리한다.</li>
 * </ul>
 */
public final class ApiObjectMapperFactory {

    private ApiObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new UtcDateTimeModule());
        objectMapper.registerModule(new DecimalAsStringModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return objectMapper;
    }
}
