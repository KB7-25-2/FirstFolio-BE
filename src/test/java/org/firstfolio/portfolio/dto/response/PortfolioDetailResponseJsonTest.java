package org.firstfolio.portfolio.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 응답 표기가 API_DOCS의 {@code GET /portfolios/current} 예시와 같은지 확인한다.
 *
 * <p>서비스 테스트는 값이 맞는지만 보고 표기는 보지 않는다. 금액이 숫자로 나가거나
 * 비율이 문자열로 나가도 값 검증은 그대로 통과하므로 여기서 따로 잡는다.</p>
 */
class PortfolioDetailResponseJsonTest {

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    private JsonNode serialize() throws Exception {
        PortfolioDetailResponse.Holding holding = new PortfolioDetailResponse.Holding(
                8101L,
                25L,
                "푸른나무 정기예금",
                "DEPOSIT_SAVINGS",
                new BigDecimal("1.000000"),
                new BigDecimal("10000000.00"),
                new BigDecimal("10080000.00"),
                "PRINCIPAL",
                null
        );

        PortfolioDetailResponse response = new PortfolioDetailResponse(
                8001L,
                1,
                new BigDecimal("2000000.00"),
                List.of(holding),
                new PortfolioDetailResponse.Summary(
                        new BigDecimal("28200000.00"),
                        new BigDecimal("30200000.00"),
                        new BigDecimal("200000.00"),
                        new BigDecimal("0.67")
                ),
                List.of(new PortfolioDetailResponse.Allocation(
                        "DEPOSIT_SAVINGS",
                        new BigDecimal("10080000.00"),
                        new BigDecimal("33.38")
                )),
                LocalDateTime.of(2026, 7, 29, 3, 0)
        );

        return objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.of(response))).get("data");
    }

    @Test
    @DisplayName("명세의 필드 이름을 그대로 쓴다")
    void usesFieldNamesFromSpec() throws Exception {
        JsonNode data = serialize();

        assertTrue(data.has("portfolio_id"));
        assertTrue(data.has("generation_no"));
        assertTrue(data.has("cash_balance"));
        assertTrue(data.has("holdings"));
        assertTrue(data.has("summary"));
        assertTrue(data.has("allocation"));
        assertTrue(data.has("valued_at"));
    }

    @Test
    @DisplayName("금액은 자릿수를 유지한 문자열이다")
    void writesMoneyAsString() throws Exception {
        JsonNode data = serialize();

        assertTrue(data.get("cash_balance").isTextual());
        assertEquals("2000000.00", data.get("cash_balance").asText());
        assertEquals("30200000.00", data.get("summary").get("total_assets").asText());
        assertEquals("10080000.00", data.get("holdings").get(0).get("valuation_amount").asText());
        assertEquals("1.000000", data.get("holdings").get(0).get("quantity").asText());
    }

    @Test
    @DisplayName("비율은 숫자로 내보낸다")
    void writesRatioAsNumber() throws Exception {
        JsonNode ratio = serialize().get("allocation").get(0).get("ratio");

        assertTrue(ratio.isNumber(), "비율은 문자열이 아니라 숫자여야 합니다.");
        assertEquals(new BigDecimal("33.38"), ratio.decimalValue());

        JsonNode profitRate = serialize().get("summary").get("profit_rate");

        assertTrue(profitRate.isNumber());
    }

    @Test
    @DisplayName("시각은 UTC 표기다")
    void writesTimeInUtc() throws Exception {
        assertEquals("2026-07-29T03:00:00Z", serialize().get("valued_at").asText());
    }

    @Test
    @DisplayName("평가 기준 시점이 없으면 null을 그대로 내보낸다 — 필드를 감추지 않는다")
    void keepsNullValuedAt() throws Exception {
        JsonNode holding = serialize().get("holdings").get(0);

        assertTrue(holding.has("valued_at"));
        assertTrue(holding.get("valued_at").isNull());
        assertEquals("PRINCIPAL", holding.get("valuation_basis").asText());
    }

    @Test
    @DisplayName("원상품을 식별할 수 있는 필드는 응답에 없다")
    void hidesSourceProductFields() throws Exception {
        String json = objectMapper.writeValueAsString(serialize());

        assertFalse(json.contains("source_product"));
        assertFalse(json.contains("source_provider"));
    }
}
