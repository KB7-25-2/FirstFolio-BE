package org.firstfolio.simulation.client.datago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * 공공데이터포털 {@code 금융위원회_증권상품시세정보}({@code getETFPriceInfo}) 응답.
 *
 * <p>채권과 같은 봉투 구조지만 {@code body.items.item}의 필드가 다르다.
 * 필드명은 camelCase다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EtfPriceResponse {

    private Response response;

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private BondBasicInfoResponse.Header header;
        private Body body;

        public BondBasicInfoResponse.Header getHeader() {
            return header;
        }

        public void setHeader(BondBasicInfoResponse.Header header) {
            this.header = header;
        }

        public Body getBody() {
            return body;
        }

        public void setBody(Body body) {
            this.body = body;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {

        private Integer totalCount;
        private Items items;

        public Integer getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }

        public Items getItems() {
            return items;
        }

        public void setItems(Items items) {
            this.items = items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {

        private List<Item> item;

        public List<Item> getItem() {
            return item;
        }

        public void setItem(List<Item> item) {
            this.item = item;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        private String basDt;

        /** 단축코드(6자리). */
        private String srtnCd;

        private String isinCd;
        private String itmsNm;

        /** 종가. */
        private BigDecimal clpr;

        /** 순자산가치. */
        private BigDecimal nav;

        /** 기초지수명. 원상품을 특정할 수 있어 사용자 응답에 넣지 않는다. */
        private String bssIdxIdxNm;

        public String getBasDt() {
            return basDt;
        }

        public void setBasDt(String basDt) {
            this.basDt = basDt;
        }

        public String getSrtnCd() {
            return srtnCd;
        }

        public void setSrtnCd(String srtnCd) {
            this.srtnCd = srtnCd;
        }

        public String getIsinCd() {
            return isinCd;
        }

        public void setIsinCd(String isinCd) {
            this.isinCd = isinCd;
        }

        public String getItmsNm() {
            return itmsNm;
        }

        public void setItmsNm(String itmsNm) {
            this.itmsNm = itmsNm;
        }

        public BigDecimal getClpr() {
            return clpr;
        }

        public void setClpr(BigDecimal clpr) {
            this.clpr = clpr;
        }

        public BigDecimal getNav() {
            return nav;
        }

        public void setNav(BigDecimal nav) {
            this.nav = nav;
        }

        public String getBssIdxIdxNm() {
            return bssIdxIdxNm;
        }

        public void setBssIdxIdxNm(String bssIdxIdxNm) {
            this.bssIdxIdxNm = bssIdxIdxNm;
        }
    }
}
