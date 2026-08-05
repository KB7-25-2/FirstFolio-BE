package org.firstfolio.simulation.client.datago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 채권 기본정보를 서비스에서 쓸 형태로 정규화한 값.
 */
public class BondBasicInfo {

    private final String isinCd;
    private final String isinCdNm;
    private final String issuerName;
    private final LocalDate issueDate;
    private final LocalDate maturityDate;
    private final BigDecimal couponRate;
    private final Integer interestIntervalMonths;
    private final String interestType;
    private final String bondCategory;
    private final String creditRating;

    public BondBasicInfo(
            String isinCd,
            String isinCdNm,
            String issuerName,
            LocalDate issueDate,
            LocalDate maturityDate,
            BigDecimal couponRate,
            Integer interestIntervalMonths,
            String interestType,
            String bondCategory,
            String creditRating
    ) {
        this.isinCd = isinCd;
        this.isinCdNm = isinCdNm;
        this.issuerName = issuerName;
        this.issueDate = issueDate;
        this.maturityDate = maturityDate;
        this.couponRate = couponRate;
        this.interestIntervalMonths = interestIntervalMonths;
        this.interestType = interestType;
        this.bondCategory = bondCategory;
        this.creditRating = creditRating;
    }

    /**
     * 오늘부터 만기까지 남은 개월 수. <b>내림</b>한다.
     *
     * <p>채권은 예·적금과 달리 "가입 기간"이 없다. 사용자가 오늘 사서 만기까지 들고 있는
     * 기간이 실제 경험 시간이므로 <b>잔존 만기</b>를 압축 대상으로 삼는다. 발행일 기준
     * 원래 만기를 쓰면 이미 지나간 기간까지 기다리게 되어 사실과 어긋난다.</p>
     *
     * <p>내림하는 이유: 올림하면 서비스 만기가 실제 만기일보다 뒤로 가서
     * "만기가 지났는데 아직 안 끝난" 상태가 생긴다.</p>
     */
    public long remainingMonths(LocalDate today) {
        if (maturityDate == null || !maturityDate.isAfter(today)) {
            return 0;
        }

        return ChronoUnit.MONTHS.between(today, maturityDate);
    }

    /** 신용등급이 없으면 국채다 (무위험이라 평가 대상이 아니다). */
    public boolean isGovernmentBond() {
        return creditRating == null || creditRating.isBlank();
    }

    /**
     * 복리채인지 여부.
     *
     * <p><b>복리채는 중간에 이자를 지급하지 않는다.</b> 이자를 원금에 더해 굴리다가 만기에
     * 한 번에 준다. 그런데 제공처는 복리채에도 {@code intPayCyclCtt}를 "12개월"처럼 채워서
     * 준다 — 이자를 계산하는 주기이지 <b>지급하는 주기가 아니다.</b></p>
     *
     * <p>이 값을 그대로 지급 주기로 쓰면 복리채가 이표채처럼 중간에 이자를 뱉는다.
     * 그래서 지급 주기를 따질 때는 이 메서드로 걸러야 한다.</p>
     */
    public boolean isCompound() {
        return interestType != null && interestType.contains("복리");
    }

    public String getIsinCd() {
        return isinCd;
    }

    public String getIsinCdNm() {
        return isinCdNm;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public BigDecimal getCouponRate() {
        return couponRate;
    }

    public Integer getInterestIntervalMonths() {
        return interestIntervalMonths;
    }

    public String getInterestType() {
        return interestType;
    }

    public String getBondCategory() {
        return bondCategory;
    }

    public String getCreditRating() {
        return creditRating;
    }
}
