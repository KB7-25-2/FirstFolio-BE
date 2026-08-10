package org.firstfolio.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * {@code real_terms_json} / {@code simulation_terms_json}을 읽고 쓴다.
 *
 * <p>API 응답과 같은 표기 규칙(snake_case)을 쓴다. 저장된 JSON이 그대로 응답에 실리므로
 * 두 곳의 표기가 어긋나면 안 된다.</p>
 */
@Component
public class TermsJsonCodec {

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public String write(Object terms) {
        try {
            return objectMapper.writeValueAsString(terms);
        } catch (Exception exception) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "상품 조건을 저장할 수 없습니다.",
                    exception
            );
        }
    }

    public JsonNode read(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    "저장된 상품 조건을 읽을 수 없습니다.",
                    exception
            );
        }
    }
}
