package org.firstfolio.simulation.client.finlife;

import java.math.BigDecimal;

/**
 * finlife의 상품 기본정보와 만기 옵션을 합친 한 건.
 *
 * <p><b>상품 하나가 아니라 "상품 × 만기" 조합 하나</b>다. finlife는 한 상품에 만기 옵션을
 * 여러 개 주는데(예: KB Star 정기예금 = 1·3·6·12·24·36개월), 만기마다 금리가 다르고
 * 압축 계산도 만기 기준이라 조합 단위로 모의 상품을 만든다.
 * API_DOCS의 예시 {@code "OO은행 정기예금 6개월"}도 같은 형태다.</p>
 */
public class FinlifeProduct {

    private final FinlifeProductType productType;
    private final String finCoNo;
    private final String finPrdtCd;
    private final int saveTrmMonths;
    private final String companyName;
    private final String productName;
    private final BigDecimal baseInterestRate;
    private final String interestRateType;
    private final String reserveType;
    private final String disclosureMonth;

    public FinlifeProduct(
            FinlifeProductType productType,
            String finCoNo,
            String finPrdtCd,
            int saveTrmMonths,
            String companyName,
            String productName,
            BigDecimal baseInterestRate,
            String interestRateType,
            String reserveType,
            String disclosureMonth
    ) {
        this.productType = productType;
        this.finCoNo = finCoNo;
        this.finPrdtCd = finPrdtCd;
        this.saveTrmMonths = saveTrmMonths;
        this.companyName = companyName;
        this.productName = productName;
        this.baseInterestRate = baseInterestRate;
        this.interestRateType = interestRateType;
        this.reserveType = reserveType;
        this.disclosureMonth = disclosureMonth;
    }

    /**
     * 내부 원상품 식별 코드.
     *
     * <p>{@code fin_prdt_cd}만으로는 금융회사 간 충돌이 나고, 만기까지 붙여도 부족하다.
     * finlife는 <b>같은 상품·같은 만기에도 이자 방식(단리/복리)과 적립 유형(정액/자유)이
     * 다른 행을 따로 준다.</b> 이 둘을 빼면 서로 다른 조건이 같은 코드가 되어 하나만 남고
     * 나머지는 조용히 중복 처리된다(실제로 KB 81행 중 31행이 사라졌다).</p>
     */
    public String sourceProductCode() {
        return String.join(
                ":",
                finCoNo,
                finPrdtCd,
                String.valueOf(saveTrmMonths),
                codeOf(interestRateType),
                codeOf(reserveType)
        );
    }

    /**
     * 내부 전용 실제 상품명. 사용자 응답에 절대 노출하지 않는다 (FUNC-032/038).
     *
     * <p>같은 상품·만기라도 조건이 다르면 관리자가 구분할 수 있어야 하므로 함께 적는다.</p>
     */
    public String sourceProductName() {
        StringBuilder name = new StringBuilder()
                .append(companyName)
                .append(" ")
                .append(productName)
                .append(" ")
                .append(saveTrmMonths)
                .append("개월");

        if (interestRateType != null && !interestRateType.isBlank()) {
            name.append(" ").append(interestRateType.trim());
        }

        if (reserveType != null && !reserveType.isBlank()) {
            name.append(" ").append(reserveType.trim());
        }

        return name.toString();
    }

    /** 코드에 넣을 수 있게 정규화한다. 값이 없으면 빈 칸으로 자리만 지킨다. */
    private static String codeOf(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.trim().replace(":", "");
    }

    public FinlifeProductType getProductType() {
        return productType;
    }

    public String getFinCoNo() {
        return finCoNo;
    }

    public String getFinPrdtCd() {
        return finPrdtCd;
    }

    public int getSaveTrmMonths() {
        return saveTrmMonths;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getBaseInterestRate() {
        return baseInterestRate;
    }

    public String getInterestRateType() {
        return interestRateType;
    }

    public String getReserveType() {
        return reserveType;
    }

    public String getDisclosureMonth() {
        return disclosureMonth;
    }
}
