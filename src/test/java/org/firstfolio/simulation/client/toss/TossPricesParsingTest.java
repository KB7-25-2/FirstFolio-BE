package org.firstfolio.simulation.client.toss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 토스증권 시세 응답 파싱 검증.
 *
 * <p>{@code lastPrice}가 숫자가 아니라 <b>문자열</b>로 온다. 금액이므로 double이 아니라
 * BigDecimal로 받아야 한다 (FUNC-036: 금액 계산에 부동소수점 금지).</p>
 */
class TossPricesParsingTest {

    /** 실제 응답 그대로. */
    private static final String SAMPLE = """
            {"result":[
              {"symbol":"005930","timestamp":"2026-08-04T17:43:44.000+09:00","lastPrice":"241500","currency":"KRW"},
              {"symbol":"035900","timestamp":"2026-08-04T17:42:44.000+09:00","lastPrice":"46200","currency":"KRW"}
            ]}
            """;

    @Test
    @DisplayName("camelCase 필드를 읽는다")
    void parsesCamelCaseFields() throws Exception {
        TossPricesResponse.Item item = TossInvestClient.parsePrices(SAMPLE).getResult().get(0);

        assertEquals("005930", item.getSymbol());
        assertEquals("KRW", item.getCurrency());
        assertEquals("2026-08-04T17:43:44.000+09:00", item.getTimestamp());
    }

    @Test
    @DisplayName("문자열로 오는 가격을 BigDecimal로 받는다")
    void parsesPriceAsBigDecimal() throws Exception {
        TossPricesResponse.Item item = TossInvestClient.parsePrices(SAMPLE).getResult().get(0);

        assertEquals(new BigDecimal("241500"), item.getLastPrice());
    }

    @Test
    @DisplayName("여러 종목을 한 응답에서 읽는다")
    void parsesMultipleSymbols() throws Exception {
        assertEquals(2, TossInvestClient.parsePrices(SAMPLE).getResult().size());
    }

    @Test
    @DisplayName("없는 종목코드는 결과에서 빠진다 — 요청 수와 응답 수가 다를 수 있다")
    void missingSymbolsAreSilentlyOmitted() throws Exception {
        // 005930,999999를 요청해도 존재하는 종목만 돌아온다. 오류가 아니다.
        String partial = """
                {"result":[
                  {"symbol":"005930","timestamp":"2026-08-04T17:43:44.000+09:00","lastPrice":"241500","currency":"KRW"}
                ]}
                """;

        assertEquals(1, TossInvestClient.parsePrices(partial).getResult().size());
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        String withNew = SAMPLE.replace(
                "\"currency\":\"KRW\"",
                "\"currency\":\"KRW\",\"brandNewField\":1"
        );

        assertNotNull(TossInvestClient.parsePrices(withNew).getResult());
    }

    @Test
    @DisplayName("토큰 응답의 snake_case 필드를 읽는다")
    void parsesTokenResponse() throws Exception {
        String json = """
                {"access_token":"abc.def.ghi","token_type":"Bearer","expires_in":86399}
                """;

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES);
        TossInvestClient.TokenResponse token =
                mapper.readValue(json, TossInvestClient.TokenResponse.class);

        assertEquals("abc.def.ghi", token.accessToken);
        assertTrue(token.expiresIn > 0);
    }
}
