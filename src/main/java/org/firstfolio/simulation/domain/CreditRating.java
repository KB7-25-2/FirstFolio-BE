package org.firstfolio.simulation.domain;

import java.util.List;

/**
 * 회사채 신용등급을 위험도로 환산한다.
 *
 * <p>국내 신용평가사(NICE·KIS·KBP)는 AAA부터 D까지 10개 등급을 쓰고, AA~B 구간에는
 * {@code +}, {@code 0}, {@code -}가 붙는다. 같은 채권이라도 평가사에 따라 표기가 달라
 * ({@code AA0} / {@code AA}) 세부 기호를 떼고 판단한다.</p>
 *
 * <p><b>1차 구분선은 투자적격(BBB- 이상)과 투기등급(BB+ 이하)이다.</b>
 * 투자적격은 원리금 지급 능력이 인정되는 구간이고, 투기등급은 부도 위험이 있는 구간이다.
 * 이 경계가 채권 위험도의 핵심이라 위험도도 여기에 맞춘다.</p>
 */
public final class CreditRating {

    /** 투자적격 등급의 알파벳 부분. */
    private static final List<String> INVESTMENT_GRADE = List.of("AAA", "AA", "A", "BBB");

    /** 투기등급의 알파벳 부분. */
    private static final List<String> SPECULATIVE_GRADE = List.of("BB", "B", "CCC", "CC", "C", "D");

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    private CreditRating() {
    }

    /**
     * {@code AA0}, {@code A+}, {@code BBB-}에서 알파벳 부분만 남긴다.
     *
     * @return 정규화한 등급. 판별할 수 없으면 null
     */
    public static String normalize(String rating) {
        if (rating == null) {
            return null;
        }

        String value = rating.trim().toUpperCase();

        // 뒤에 붙는 +, -, 0을 떼어낸다. AA0 -> AA, A+ -> A, BBB- -> BBB
        while (!value.isEmpty()) {
            char last = value.charAt(value.length() - 1);

            if (last == '+' || last == '-' || last == '0') {
                value = value.substring(0, value.length() - 1);
            } else {
                break;
            }
        }

        if (value.isEmpty()) {
            return null;
        }

        if (INVESTMENT_GRADE.contains(value) || SPECULATIVE_GRADE.contains(value)) {
            return value;
        }

        return null;
    }

    /** BBB- 이상. 원리금 지급 능력이 인정되는 구간이다. */
    public static boolean isInvestmentGrade(String rating) {
        return INVESTMENT_GRADE.contains(normalize(rating));
    }

    /**
     * 위험도.
     *
     * <ul>
     *   <li>{@code LOW} — 등급이 없는 국채. 발행 주체가 국가라 신용평가 대상이 아니다.</li>
     *   <li>{@code MEDIUM} — 투자적격 회사채(AAA~BBB-).</li>
     *   <li>{@code HIGH} — 투기등급(BB+ 이하) 또는 판별할 수 없는 등급.</li>
     * </ul>
     *
     * <p>AAA와 A0을 같은 {@code MEDIUM}으로 두는 이유: 둘 다 원리금 지급 능력이 인정되는
     * 구간이라 "부도 위험"이라는 축에서는 같은 편이다. 그 안의 차이는 위험도가 아니라
     * {@code real_terms.credit_rating}과 표면이율로 드러내는 것이 정확하다
     * (같은 만기에 AAA 1.9% / A0 5.4%처럼).</p>
     */
    public static String riskLevel(String rating) {
        String normalized = normalize(rating);

        if (normalized == null) {
            // 등급 자체가 없으면 국채다. 판별 불가한 값이 들어온 경우는 호출부에서 거른다.
            return rating == null || rating.isBlank() ? RISK_LOW : RISK_HIGH;
        }

        return INVESTMENT_GRADE.contains(normalized) ? RISK_MEDIUM : RISK_HIGH;
    }
}
