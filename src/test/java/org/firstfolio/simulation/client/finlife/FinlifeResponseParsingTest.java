package org.firstfolio.simulation.client.finlife;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * finlife 응답 파싱 검증.
 *
 * <p>제공처 응답은 표기가 섞여 있다. 대부분 snake_case인데 {@code baseList}/{@code optionList}만
 * camelCase다. 전체를 snake_case로 읽으면 이 두 배열이 조용히 null이 되고, {@code err_cd}는
 * 정상 파싱되어 <b>"성공했는데 0건"</b>으로 넘어간다. 실제로 그 상태로 배포까지 갔다.
 * 서비스 테스트는 클라이언트를 모킹해서 이 구간을 전혀 통과하지 않는다.</p>
 */
class FinlifeResponseParsingTest {

    /** 실제 응답에서 필요한 부분만 발췌했다. */
    private static final String SAMPLE = """
            {
              "result": {
                "prdt_div": "D",
                "total_count": 38,
                "max_page_no": 2,
                "now_page_no": 1,
                "err_cd": "000",
                "err_msg": "정상",
                "baseList": [
                  {
                    "dcls_month": "202607",
                    "fin_co_no": "0010927",
                    "fin_prdt_cd": "010300100335",
                    "kor_co_nm": "국민은행",
                    "fin_prdt_nm": "KB Star 정기예금",
                    "join_way": "인터넷,스마트폰",
                    "max_limit": null
                  }
                ],
                "optionList": [
                  {
                    "dcls_month": "202607",
                    "fin_co_no": "0010927",
                    "fin_prdt_cd": "010300100335",
                    "intr_rate_type": "S",
                    "intr_rate_type_nm": "단리",
                    "save_trm": "6",
                    "intr_rate": 2.35,
                    "intr_rate2": 3.0
                  }
                ]
              }
            }
            """;

    @Test
    @DisplayName("camelCase인 baseList·optionList를 놓치지 않는다")
    void parsesCamelCaseLists() throws Exception {
        FinlifeResponse.Result result = FinlifeClient.parse(SAMPLE).getResult();

        assertNotNull(result.getBaseList(), "baseList를 읽지 못했습니다.");
        assertNotNull(result.getOptionList(), "optionList를 읽지 못했습니다.");
        assertEquals(1, result.getBaseList().size());
        assertEquals(1, result.getOptionList().size());
    }

    @Test
    @DisplayName("snake_case인 메타 필드도 함께 읽는다")
    void parsesSnakeCaseMetadata() throws Exception {
        FinlifeResponse.Result result = FinlifeClient.parse(SAMPLE).getResult();

        assertTrue(result.isSuccess());
        assertEquals("000", result.getErrCd());
        assertEquals(38, result.getTotalCount());
        assertEquals(2, result.getMaxPageNo());
    }

    @Test
    @DisplayName("상품 기본정보 필드를 읽는다")
    void parsesBaseFields() throws Exception {
        FinlifeResponse.Base base = FinlifeClient.parse(SAMPLE).getResult().getBaseList().get(0);

        assertEquals("0010927", base.getFinCoNo());
        assertEquals("010300100335", base.getFinPrdtCd());
        assertEquals("국민은행", base.getKorCoNm());
        assertEquals("KB Star 정기예금", base.getFinPrdtNm());
    }

    @Test
    @DisplayName("만기 옵션 필드를 읽고 금리는 BigDecimal로 받는다")
    void parsesOptionFields() throws Exception {
        FinlifeResponse.Option option =
                FinlifeClient.parse(SAMPLE).getResult().getOptionList().get(0);

        assertEquals("0010927", option.getFinCoNo());
        assertEquals("6", option.getSaveTrm());
        assertEquals(new BigDecimal("2.35"), option.getIntrRate());
        assertEquals("단리", option.getIntrRateTypeNm());
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        String withNewField = SAMPLE.replace(
                "\"prdt_div\": \"D\",",
                "\"prdt_div\": \"D\", \"brand_new_field\": \"x\","
        );

        assertNotNull(FinlifeClient.parse(withNewField).getResult().getBaseList());
    }
}
