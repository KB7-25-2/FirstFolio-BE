package org.firstfolio.simulation.client.datago;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ETF 시세 응답 파싱 검증.
 *
 * <p>가격은 문자열로 오고, 필드명은 camelCase다. 채권과 같은 봉투 구조지만 항목 필드가 다르다.</p>
 */
class EtfPriceParsingTest {

    /** 실제 응답에서 필요한 부분만 발췌했다. */
    private static final String SAMPLE = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                "body": {
                  "numOfRows": 5, "pageNo": 1, "totalCount": 1,
                  "items": {
                    "item": [
                      {
                        "basDt": "20260803",
                        "srtnCd": "069500",
                        "isinCd": "KR7069500007",
                        "itmsNm": "KODEX 200",
                        "clpr": "99105",
                        "nav": "99117.94",
                        "bssIdxIdxNm": "코스피 200",
                        "mrktTotAmt": "22397700000000"
                      }
                    ]
                  }
                }
              }
            }
            """;

    @Test
    @DisplayName("camelCase 필드를 읽는다")
    void parsesCamelCaseFields() throws Exception {
        EtfPriceResponse.Item item = EtfPriceClient
                .parse(SAMPLE).getResponse().getBody().getItems().getItem().get(0);

        assertEquals("069500", item.getSrtnCd());
        assertEquals("KR7069500007", item.getIsinCd());
        assertEquals("KODEX 200", item.getItmsNm());
        assertEquals("코스피 200", item.getBssIdxIdxNm());
    }

    @Test
    @DisplayName("문자열로 오는 종가·NAV를 BigDecimal로 받는다")
    void parsesPricesAsBigDecimal() throws Exception {
        EtfPriceResponse.Item item = EtfPriceClient
                .parse(SAMPLE).getResponse().getBody().getItems().getItem().get(0);

        assertEquals(new BigDecimal("99105"), item.getClpr());
        assertEquals(new BigDecimal("99117.94"), item.getNav());
    }

    @Test
    @DisplayName("resultCode 00을 성공으로 본다")
    void readsResultCode() throws Exception {
        assertTrue(EtfPriceClient.parse(SAMPLE).getResponse().getHeader().isSuccess());
    }

    /** 결과가 1건일 때 {@code item}이 배열이 아니라 객체 하나로 온다. */
    private static final String SINGLE_OBJECT_ITEM = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                "body": {
                  "numOfRows": 5, "pageNo": 1, "totalCount": 1,
                  "items": {
                    "item": {
                      "basDt": "20260803",
                      "srtnCd": "069500",
                      "isinCd": "KR7069500007",
                      "itmsNm": "KODEX 200",
                      "clpr": "99105",
                      "nav": "99117.94",
                      "bssIdxIdxNm": "코스피 200"
                    }
                  }
                }
              }
            }
            """;

    @Test
    @DisplayName("결과가 1건이면 item이 객체로 와도 배열로 받는다")
    void acceptsSingleItemAsArray() throws Exception {
        var items = EtfPriceClient.parse(SINGLE_OBJECT_ITEM)
                .getResponse().getBody().getItems().getItem();

        assertEquals(1, items.size());
        assertEquals("069500", items.get(0).getSrtnCd());
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        String withNew = SAMPLE.replace(
                "\"basDt\": \"20260803\",",
                "\"basDt\": \"20260803\", \"brandNewField\": \"x\","
        );

        assertNotNull(EtfPriceClient.parse(withNew).getResponse().getBody().getItems());
    }
}
