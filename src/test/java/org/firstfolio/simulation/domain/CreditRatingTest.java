package org.firstfolio.simulation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditRatingTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("+, 0, - 기호를 떼고 알파벳 등급만 남긴다")
    @CsvSource({
            "AAA, AAA",
            "AA+, AA",
            "AA0, AA",
            "AA-, AA",
            "AA,  AA",
            "A+,  A",
            "A0,  A",
            "A,   A",
            "BBB0, BBB",
            "BBB-, BBB",
            "BB+, BB",
            "B,   B",
            "CCC, CCC",
            "D,   D"
    })
    void normalizesModifiers(String raw, String expected) {
        assertEquals(expected, CreditRating.normalize(raw));
    }

    @ParameterizedTest
    @DisplayName("평가사마다 표기가 달라도 같은 등급으로 본다")
    @ValueSource(strings = {"AA0", "AA", "aa0", " AA0 "})
    void treatsAgencyNotationsAsSame(String raw) {
        assertEquals("AA", CreditRating.normalize(raw));
    }

    @ParameterizedTest
    @DisplayName("투자적격은 BBB- 이상이다")
    @ValueSource(strings = {"AAA", "AA+", "AA0", "A0", "A-", "BBB+", "BBB-"})
    void recognizesInvestmentGrade(String rating) {
        assertTrue(CreditRating.isInvestmentGrade(rating));
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel(rating));
    }

    @ParameterizedTest
    @DisplayName("투기등급은 BB+ 이하다")
    @ValueSource(strings = {"BB+", "BB0", "BB-", "B", "CCC", "CC", "C", "D"})
    void recognizesSpeculativeGrade(String rating) {
        assertFalse(CreditRating.isInvestmentGrade(rating));
        assertEquals(CreditRating.RISK_HIGH, CreditRating.riskLevel(rating));
    }

    @Test
    @DisplayName("BBB와 BB의 경계를 헷갈리지 않는다 — 투자적격과 투기등급이 갈리는 지점")
    void distinguishesBbbFromBb() {
        assertTrue(CreditRating.isInvestmentGrade("BBB-"));
        assertFalse(CreditRating.isInvestmentGrade("BB+"));
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel("BBB-"));
        assertEquals(CreditRating.RISK_HIGH, CreditRating.riskLevel("BB+"));
    }

    @Test
    @DisplayName("등급이 없으면 국채로 보고 LOW다")
    void treatsMissingRatingAsGovernmentBond() {
        assertEquals(CreditRating.RISK_LOW, CreditRating.riskLevel(null));
        assertEquals(CreditRating.RISK_LOW, CreditRating.riskLevel("  "));
        assertNull(CreditRating.normalize(null));
    }

    @Test
    @DisplayName("알 수 없는 등급은 안전한 쪽이 아니라 HIGH로 본다")
    void treatsUnknownRatingAsHigh() {
        assertEquals(CreditRating.RISK_HIGH, CreditRating.riskLevel("XYZ"));
        assertEquals(CreditRating.RISK_HIGH, CreditRating.riskLevel("등급없음"));
        assertNull(CreditRating.normalize("XYZ"));
    }

    @Test
    @DisplayName("실제 수집 데이터의 등급을 처리한다")
    void handlesRealCollectedRatings() {
        // 현대자동차 AAA, 현대제철 AA0(KBP는 AA), 대한항공 A0(KBP는 A)
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel("AAA"));
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel("AA0"));
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel("AA"));
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel("A0"));
        assertEquals(CreditRating.RISK_MEDIUM, CreditRating.riskLevel("A"));
    }
}
