package org.firstfolio.simulation.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * 채권의 실제 조건. {@code financial_products.real_terms_json}에 저장한다.
 *
 * <p><b>발행사명·종목코드(ISIN)·정확한 만기일은 담지 않는다.</b> {@code real_terms}는
 * 사용자 응답에 그대로 실리는데, 그 값들은 원상품을 특정할 수 있는 식별 정보다
 * (FUNC-032: 실제 상품명·코드·내부 매핑은 사용자에게 노출하지 않는다).
 * 관리자에게는 별도 필드 {@code source_product_name}으로 제공한다.</p>
 *
 * <p>신용등급은 상품의 성질이지 식별자가 아니라서 포함한다. 오히려 국채와 회사채,
 * 등급별 금리 차이를 배우는 것이 채권을 넣는 이유다.</p>
 */
public class BondRealTerms {

    /** 표면이율(%). API_DOCS의 금리 표기와 맞춰 숫자로 내보낸다. */
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal couponRate;

    /** 잔존 만기(개월, 내림). 발행일 기준 원래 만기가 아니다. */
    private Integer maturityMonths;

    /**
     * 이자 지급 주기(개월). 이표채는 보통 3 또는 6이다.
     * <b>복리채는 중간 지급이 없어 값을 넣지 않는다.</b>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer interestIntervalMonths;

    /** 이표채, 할인채 등. */
    private String interestType;

    /** 국채, 일반회사채 등. */
    private String bondCategory;

    /** AAA, AA0, A0 등. 국채는 평가 대상이 아니라 없다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String creditRating;

    public BigDecimal getCouponRate() {
        return couponRate;
    }

    public void setCouponRate(BigDecimal couponRate) {
        this.couponRate = couponRate;
    }

    public Integer getMaturityMonths() {
        return maturityMonths;
    }

    public void setMaturityMonths(Integer maturityMonths) {
        this.maturityMonths = maturityMonths;
    }

    public Integer getInterestIntervalMonths() {
        return interestIntervalMonths;
    }

    public void setInterestIntervalMonths(Integer interestIntervalMonths) {
        this.interestIntervalMonths = interestIntervalMonths;
    }

    public String getInterestType() {
        return interestType;
    }

    public void setInterestType(String interestType) {
        this.interestType = interestType;
    }

    public String getBondCategory() {
        return bondCategory;
    }

    public void setBondCategory(String bondCategory) {
        this.bondCategory = bondCategory;
    }

    public String getCreditRating() {
        return creditRating;
    }

    public void setCreditRating(String creditRating) {
        this.creditRating = creditRating;
    }
}
