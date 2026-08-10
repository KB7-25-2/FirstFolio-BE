package org.firstfolio.simulation.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * 원상품의 실제 조건. {@code financial_products.real_terms_json}에 저장하고
 * 상품 상세에서 "실제 상품은 N개월 만기"로 병기한다 (FUNC-039).
 */
public class RealTerms {

    /** 기본금리(%). 우대금리는 조건부라 쓰지 않는다. API_DOCS 예시가 숫자라 숫자로 내보낸다. */
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal interestRate;

    private Integer maturityMonths;

    /** 이자 지급 주기. 예·적금은 만기일시지급. */
    private String interestInterval;

    /**
     * {@code interestInterval}의 출처.
     *
     * <p>finlife는 이자 지급 주기를 주지 않는다. 정기예금·정기적금이 만기일시지급인 점에
     * 근거한 <b>가정치</b>임을 데이터에 남겨, 출처 없는 수치를 사실처럼 쓰지 않게 한다
     * (FUNC-032 예외/제한사항).</p>
     */
    private String interestIntervalSource;

    /** SIMPLE(단리) 또는 COMPOUND(복리). */
    private String interestRateType;

    /** 적금만 해당. FIXED(정액적립식) 또는 FLEXIBLE(자유적립식). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reserveType;

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public Integer getMaturityMonths() {
        return maturityMonths;
    }

    public void setMaturityMonths(Integer maturityMonths) {
        this.maturityMonths = maturityMonths;
    }

    public String getInterestInterval() {
        return interestInterval;
    }

    public void setInterestInterval(String interestInterval) {
        this.interestInterval = interestInterval;
    }

    public String getInterestIntervalSource() {
        return interestIntervalSource;
    }

    public void setInterestIntervalSource(String interestIntervalSource) {
        this.interestIntervalSource = interestIntervalSource;
    }

    public String getInterestRateType() {
        return interestRateType;
    }

    public void setInterestRateType(String interestRateType) {
        this.interestRateType = interestRateType;
    }

    public String getReserveType() {
        return reserveType;
    }

    public void setReserveType(String reserveType) {
        this.reserveType = reserveType;
    }
}
