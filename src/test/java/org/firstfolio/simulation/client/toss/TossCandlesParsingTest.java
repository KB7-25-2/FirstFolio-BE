package org.firstfolio.simulation.client.toss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TossCandlesParsingTest {

    /** 2026-08-21 운영 IP 실호출에서 확인한 응답 구조. */
    private static final String SAMPLE = """
            {"result":{"candles":[
              {"timestamp":"2026-08-21T00:00:00.000+09:00","openPrice":"273000","highPrice":"285000","lowPrice":"266000","closePrice":"281500","volume":"48552856","currency":"KRW"},
              {"timestamp":"2026-08-20T00:00:00.000+09:00","openPrice":"258000","highPrice":"274000","lowPrice":"252000","closePrice":"273000","volume":"46036748","currency":"KRW"}
            ],"nextBefore":"2026-08-19T00:00:00.000+09:00"}}
            """;

    @Test
    @DisplayName("문자열 OHLCV와 camelCase 필드를 손실 없이 읽는다")
    void parsesDailyCandles() throws Exception {
        TossCandlesResponse.Result result = TossInvestClient.parseCandles(SAMPLE).getResult();
        TossCandlesResponse.Item latest = result.getCandles().get(0);

        assertEquals("2026-08-21T00:00:00.000+09:00", latest.getTimestamp());
        assertEquals(new BigDecimal("273000"), latest.getOpenPrice());
        assertEquals(new BigDecimal("285000"), latest.getHighPrice());
        assertEquals(new BigDecimal("266000"), latest.getLowPrice());
        assertEquals(new BigDecimal("281500"), latest.getClosePrice());
        assertEquals(new BigDecimal("48552856"), latest.getVolume());
        assertEquals("KRW", latest.getCurrency());
        assertEquals("2026-08-19T00:00:00.000+09:00", result.getNextBefore());
    }
}
