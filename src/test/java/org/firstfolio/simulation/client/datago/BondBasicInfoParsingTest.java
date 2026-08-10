package org.firstfolio.simulation.client.datago;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 공공데이터포털 응답 파싱 검증.
 *
 * <p>이 API는 필드명이 <b>camelCase</b>다. snake_case인 finlife와 정반대라 매퍼를 잘못 쓰면
 * 예외 없이 전부 null이 되고 "성공했는데 0건"으로 조용히 넘어간다. finlife에서 실제로 겪은
 * 사고라 여기서 고정한다.</p>
 */
class BondBasicInfoParsingTest {

    /** 회사채. 결과가 1건이라 {@code item}이 배열이 아니라 객체로 온다. */
    private static final String CORPORATE_BOND = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                "body": {
                  "numOfRows": 1, "pageNo": 1, "totalCount": 1,
                  "items": {
                    "item": {
                      "basDt": "20260804",
                      "isinCd": "KR6003492DB8",
                      "isinCdNm": "대한항공 105-2",
                      "bondIsurNm": "대한항공",
                      "bondIssuDt": "20231110",
                      "bondExprDt": "20261110",
                      "bondSrfcInrt": "5.397",
                      "intPayCyclCtt": "3개월",
                      "bondIntTcdNm": "이표채",
                      "scrsItmsKcdNm": "일반회사채",
                      "niceScrsItmsKcdNm": "A0",
                      "kisScrsItmsKcdNm": "A0",
                      "kbpScrsItmsKcdNm": "A",
                      "grnDcdNm": "무보증",
                      "pamtRdptMcdNm": "만기상환"
                    }
                  }
                }
              }
            }
            """;

    /** 국채. 신용등급 필드가 공백으로 온다. */
    private static final String GOVERNMENT_BOND = CORPORATE_BOND
            .replace("\"niceScrsItmsKcdNm\": \"A0\"", "\"niceScrsItmsKcdNm\": \" \"")
            .replace("\"scrsItmsKcdNm\": \"일반회사채\"", "\"scrsItmsKcdNm\": \"국채\"");

    @Test
    @DisplayName("camelCase 필드를 읽는다")
    void parsesCamelCaseFields() throws Exception {
        BondBasicInfoResponse.Item item = BondIssueInfoClient
                .parse(CORPORATE_BOND).getResponse().getBody().getItems().getItem().get(0);

        assertEquals("KR6003492DB8", item.getIsinCd());
        assertEquals("대한항공 105-2", item.getIsinCdNm());
        assertEquals("20261110", item.getBondExprDt());
        assertEquals("5.397", item.getBondSrfcInrt());
    }

    @Test
    @DisplayName("결과가 1건이면 item이 객체로 오는데 이것도 배열로 받는다")
    void acceptsSingleItemAsArray() throws Exception {
        assertEquals(
                1,
                BondIssueInfoClient.parse(CORPORATE_BOND)
                        .getResponse().getBody().getItems().getItem().size()
        );
    }

    @Test
    @DisplayName("resultCode 00을 성공으로 본다")
    void readsResultCode() throws Exception {
        assertTrue(BondIssueInfoClient.parse(CORPORATE_BOND).getResponse().getHeader().isSuccess());
    }

    @Test
    @DisplayName("날짜·금리·이자주기를 정규화한다")
    void normalizesFields() throws Exception {
        BondBasicInfo info = BondIssueInfoClient.toBondBasicInfo(
                BondIssueInfoClient.parse(CORPORATE_BOND)
                        .getResponse().getBody().getItems().getItem().get(0)
        );

        assertEquals(LocalDate.of(2026, 11, 10), info.getMaturityDate());
        assertEquals(new BigDecimal("5.397"), info.getCouponRate());
        assertEquals(3, info.getInterestIntervalMonths());
        assertEquals("A0", info.getCreditRating());
        assertFalse(info.isGovernmentBond());
    }

    @Test
    @DisplayName("신용등급이 공백이면 국채로 본다")
    void treatsBlankRatingAsGovernmentBond() throws Exception {
        BondBasicInfo info = BondIssueInfoClient.toBondBasicInfo(
                BondIssueInfoClient.parse(GOVERNMENT_BOND)
                        .getResponse().getBody().getItems().getItem().get(0)
        );

        assertNull(info.getCreditRating(), "공백은 null로 정규화해야 합니다.");
        assertTrue(info.isGovernmentBond());
    }

    @Test
    @DisplayName("잔존 만기를 개월 단위로 내림한다")
    void floorsRemainingMonths() throws Exception {
        BondBasicInfo info = BondIssueInfoClient.toBondBasicInfo(
                BondIssueInfoClient.parse(CORPORATE_BOND)
                        .getResponse().getBody().getItems().getItem().get(0)
        );

        // 만기 2026-11-10
        assertEquals(3, info.remainingMonths(LocalDate.of(2026, 8, 4)));
        assertEquals(3, info.remainingMonths(LocalDate.of(2026, 8, 10)));
        // 하루 차이로 3개월을 못 채우면 2개월이다 (내림).
        assertEquals(2, info.remainingMonths(LocalDate.of(2026, 8, 11)));
    }

    @Test
    @DisplayName("만기가 지났으면 0개월이다")
    void returnsZeroForMaturedBond() throws Exception {
        BondBasicInfo info = BondIssueInfoClient.toBondBasicInfo(
                BondIssueInfoClient.parse(CORPORATE_BOND)
                        .getResponse().getBody().getItems().getItem().get(0)
        );

        assertEquals(0, info.remainingMonths(LocalDate.of(2027, 1, 1)));
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        String withNew = CORPORATE_BOND.replace(
                "\"basDt\": \"20260804\",",
                "\"basDt\": \"20260804\", \"brandNewField\": \"x\","
        );

        assertNotNull(BondIssueInfoClient.parse(withNew).getResponse().getBody().getItems());
    }
}
