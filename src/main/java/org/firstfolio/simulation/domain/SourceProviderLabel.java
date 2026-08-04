package org.firstfolio.simulation.domain;

/**
 * 원천 데이터 제공처를 <b>사용자에게 보여줄 표시명</b>으로 바꾼다.
 *
 * <p>API_DOCS {@code GET /financial-products/{product_id}}의 처리 규칙:
 * <i>"출처는 사용자에게 공개 가능한 제공처와 기준 시점만 반환한다."</i></p>
 *
 * <p>내부 식별자({@code FSS_FINLIFE}, {@code TOSSINVEST} 등)를 그대로 주면
 * 어느 시장의 어떤 종류 상품인지 좁혀지고, 금리·만기 같은 조건과 조합하면 원상품을
 * 특정할 수 있다. 그래서 자산군 수준으로만 뭉뚱그린다.</p>
 */
public final class SourceProviderLabel {

    /** 어느 제공처인지 알 수 없을 때. 근거 없는 값을 만들지 않는다. */
    private static final String UNKNOWN = "공개 금융 데이터";

    private SourceProviderLabel() {
    }

    public static String of(String sourceProvider) {
        if (sourceProvider == null || sourceProvider.isBlank()) {
            return UNKNOWN;
        }

        switch (sourceProvider.trim().toUpperCase()) {
            case "FSS_FINLIFE":
                return "공개 예·적금 상품 정보";
            case "DATA_GO_KR_BOND":
                return "공개 채권 정보";
            case "DATA_GO_KR_ETF":
                return "공개 상장지수상품 시세";
            case "TOSSINVEST":
                return "공개 주식 시세";
            default:
                return UNKNOWN;
        }
    }
}
