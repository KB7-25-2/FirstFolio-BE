package org.firstfolio.simulation.client.datago;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 공공데이터포털(data.go.kr) 응답 파싱 규칙.
 *
 * <p><b>필드명이 camelCase다</b>({@code basDt}, {@code isinCd}, {@code bondExprDt}).
 * snake_case인 finlife와 정반대이므로 <b>매퍼를 공유하면 안 된다.</b>
 * 이름 규칙이 어긋나면 예외 없이 전부 null이 되고 "성공했는데 0건"으로 조용히 넘어간다.</p>
 *
 * <p>결과가 1건일 때 {@code items.item}이 배열이 아니라 객체 하나로 오므로
 * {@code ACCEPT_SINGLE_VALUE_AS_ARRAY}를 켠다.</p>
 */
public final class DataGoKrClientSupport {

    /** 이름 규칙을 바꾸지 않는다 (camelCase 그대로). */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private DataGoKrClientSupport() {
    }

    public static <T> T parse(String json, Class<T> type) throws IOException {
        return MAPPER.readValue(json, type);
    }
}
