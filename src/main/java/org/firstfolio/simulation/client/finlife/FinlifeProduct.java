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
     * 내부 원상품 식별 코드. {@code fin_prdt_cd}만으로는 금융회사 간 충돌이 나므로
     * 회사 코드와 만기까지 포함한다.
     */
    public String sourceProductCode() {
        return finCoNo + ":" + finPrdtCd + ":" + saveTrmMonths;
    }

    /**
     * 내부 전용 실제 상품명. 사용자 응답에 절대 노출하지 않는다 (FUNC-032/038).
     */
    public String sourceProductName() {
        return companyName + " " + productName + " " + saveTrmMonths + "개월";
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
