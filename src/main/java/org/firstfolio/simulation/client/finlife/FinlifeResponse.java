package org.firstfolio.simulation.client.finlife;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * 금감원 finlife 오픈API 응답.
 *
 * <p>상품 기본정보({@code baseList})와 만기별 금리({@code optionList})가 분리돼 오며,
 * {@code fin_co_no + fin_prdt_cd}로 묶인다. 한 상품에 만기 옵션이 여러 개다.</p>
 *
 * <p>제공처가 필드를 추가해도 깨지지 않도록 모르는 필드는 무시한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinlifeResponse {

    private Result result;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private String errCd;
        private String errMsg;
        private Integer totalCount;
        private Integer maxPageNo;
        private Integer nowPageNo;

        /**
         * finlife 응답은 표기가 섞여 있다. 대부분은 snake_case({@code err_cd},
         * {@code total_count})인데 이 두 배열만 camelCase다. snake_case 규칙에 맡기면
         * {@code base_list}를 찾다가 못 찾고 null이 되는데, {@code err_cd}는 정상 파싱되어
         * "성공인데 결과가 0건"으로 조용히 넘어간다. 그래서 키를 직접 지정한다.
         */
        @JsonProperty("baseList")
        private List<Base> baseList;

        @JsonProperty("optionList")
        private List<Option> optionList;

        /** {@code 000}이 정상이다. */
        public boolean isSuccess() {
            return "000".equals(errCd);
        }

        public String getErrCd() {
            return errCd;
        }

        public void setErrCd(String errCd) {
            this.errCd = errCd;
        }

        public String getErrMsg() {
            return errMsg;
        }

        public void setErrMsg(String errMsg) {
            this.errMsg = errMsg;
        }

        public Integer getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }

        public Integer getMaxPageNo() {
            return maxPageNo;
        }

        public void setMaxPageNo(Integer maxPageNo) {
            this.maxPageNo = maxPageNo;
        }

        public Integer getNowPageNo() {
            return nowPageNo;
        }

        public void setNowPageNo(Integer nowPageNo) {
            this.nowPageNo = nowPageNo;
        }

        public List<Base> getBaseList() {
            return baseList;
        }

        public void setBaseList(List<Base> baseList) {
            this.baseList = baseList;
        }

        public List<Option> getOptionList() {
            return optionList;
        }

        public void setOptionList(List<Option> optionList) {
            this.optionList = optionList;
        }
    }

    /** 상품 기본정보. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Base {

        private String finCoNo;
        private String finPrdtCd;
        private String korCoNm;
        private String finPrdtNm;
        private String joinWay;
        private String etcNote;
        private String dclsMonth;

        public String getFinCoNo() {
            return finCoNo;
        }

        public void setFinCoNo(String finCoNo) {
            this.finCoNo = finCoNo;
        }

        public String getFinPrdtCd() {
            return finPrdtCd;
        }

        public void setFinPrdtCd(String finPrdtCd) {
            this.finPrdtCd = finPrdtCd;
        }

        public String getKorCoNm() {
            return korCoNm;
        }

        public void setKorCoNm(String korCoNm) {
            this.korCoNm = korCoNm;
        }

        public String getFinPrdtNm() {
            return finPrdtNm;
        }

        public void setFinPrdtNm(String finPrdtNm) {
            this.finPrdtNm = finPrdtNm;
        }

        public String getJoinWay() {
            return joinWay;
        }

        public void setJoinWay(String joinWay) {
            this.joinWay = joinWay;
        }

        public String getEtcNote() {
            return etcNote;
        }

        public void setEtcNote(String etcNote) {
            this.etcNote = etcNote;
        }

        public String getDclsMonth() {
            return dclsMonth;
        }

        public void setDclsMonth(String dclsMonth) {
            this.dclsMonth = dclsMonth;
        }
    }

    /**
     * 만기별 금리 옵션.
     *
     * <p>{@code rsrvType}(적립 유형)은 적금 응답에만 있다.</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Option {

        private String finCoNo;
        private String finPrdtCd;
        private String saveTrm;
        private BigDecimal intrRate;
        private BigDecimal intrRate2;
        private String intrRateType;
        private String intrRateTypeNm;
        private String rsrvType;
        private String rsrvTypeNm;

        public String getFinCoNo() {
            return finCoNo;
        }

        public void setFinCoNo(String finCoNo) {
            this.finCoNo = finCoNo;
        }

        public String getFinPrdtCd() {
            return finPrdtCd;
        }

        public void setFinPrdtCd(String finPrdtCd) {
            this.finPrdtCd = finPrdtCd;
        }

        public String getSaveTrm() {
            return saveTrm;
        }

        public void setSaveTrm(String saveTrm) {
            this.saveTrm = saveTrm;
        }

        public BigDecimal getIntrRate() {
            return intrRate;
        }

        public void setIntrRate(BigDecimal intrRate) {
            this.intrRate = intrRate;
        }

        public BigDecimal getIntrRate2() {
            return intrRate2;
        }

        public void setIntrRate2(BigDecimal intrRate2) {
            this.intrRate2 = intrRate2;
        }

        public String getIntrRateType() {
            return intrRateType;
        }

        public void setIntrRateType(String intrRateType) {
            this.intrRateType = intrRateType;
        }

        public String getIntrRateTypeNm() {
            return intrRateTypeNm;
        }

        public void setIntrRateTypeNm(String intrRateTypeNm) {
            this.intrRateTypeNm = intrRateTypeNm;
        }

        public String getRsrvType() {
            return rsrvType;
        }

        public void setRsrvType(String rsrvType) {
            this.rsrvType = rsrvType;
        }

        public String getRsrvTypeNm() {
            return rsrvTypeNm;
        }

        public void setRsrvTypeNm(String rsrvTypeNm) {
            this.rsrvTypeNm = rsrvTypeNm;
        }
    }
}
