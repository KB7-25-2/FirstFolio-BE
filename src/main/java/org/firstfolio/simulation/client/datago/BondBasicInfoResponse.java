package org.firstfolio.simulation.client.datago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 공공데이터포털 {@code 금융위원회_채권기본정보}({@code getBondBasiInfo_V2}) 응답.
 *
 * <p><b>필드명이 camelCase다</b>({@code basDt}, {@code isinCd}). snake_case인 finlife와 정반대라
 * 매퍼를 공유하면 전부 null이 된다. {@link DataGoKrClientSupport}의 매퍼를 쓴다.</p>
 *
 * <p>같은 종목이 영업일마다 한 행씩 쌓여 있어({@code isinCd}만으로 조회하면 500~2,000건)
 * {@code basDt}를 반드시 함께 지정해야 최신 스냅샷을 얻는다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BondBasicInfoResponse {

    private Response response;

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private Header header;
        private Body body;

        public Header getHeader() {
            return header;
        }

        public void setHeader(Header header) {
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
    public static class Header {

        private String resultCode;
        private String resultMsg;

        /** {@code 00}이 정상이다. */
        public boolean isSuccess() {
            return "00".equals(resultCode);
        }

        public String getResultCode() {
            return resultCode;
        }

        public void setResultCode(String resultCode) {
            this.resultCode = resultCode;
        }

        public String getResultMsg() {
            return resultMsg;
        }

        public void setResultMsg(String resultMsg) {
            this.resultMsg = resultMsg;
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

    /**
     * 결과가 1건일 때 {@code item}이 배열이 아니라 객체 하나로 온다.
     * 매퍼에서 {@code ACCEPT_SINGLE_VALUE_AS_ARRAY}로 처리한다.
     */
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
        private String isinCd;
        private String isinCdNm;
        private String bondIsurNm;
        private String bondIssuDt;
        private String bondExprDt;
        private String bondSrfcInrt;
        private String intPayCyclCtt;
        private String bondIntTcdNm;
        private String scrsItmsKcdNm;
        private String niceScrsItmsKcdNm;
        private String kisScrsItmsKcdNm;
        private String kbpScrsItmsKcdNm;
        private String grnDcdNm;
        private String pamtRdptMcdNm;

        public String getBasDt() {
            return basDt;
        }

        public void setBasDt(String basDt) {
            this.basDt = basDt;
        }

        public String getIsinCd() {
            return isinCd;
        }

        public void setIsinCd(String isinCd) {
            this.isinCd = isinCd;
        }

        public String getIsinCdNm() {
            return isinCdNm;
        }

        public void setIsinCdNm(String isinCdNm) {
            this.isinCdNm = isinCdNm;
        }

        public String getBondIsurNm() {
            return bondIsurNm;
        }

        public void setBondIsurNm(String bondIsurNm) {
            this.bondIsurNm = bondIsurNm;
        }

        public String getBondIssuDt() {
            return bondIssuDt;
        }

        public void setBondIssuDt(String bondIssuDt) {
            this.bondIssuDt = bondIssuDt;
        }

        public String getBondExprDt() {
            return bondExprDt;
        }

        public void setBondExprDt(String bondExprDt) {
            this.bondExprDt = bondExprDt;
        }

        public String getBondSrfcInrt() {
            return bondSrfcInrt;
        }

        public void setBondSrfcInrt(String bondSrfcInrt) {
            this.bondSrfcInrt = bondSrfcInrt;
        }

        public String getIntPayCyclCtt() {
            return intPayCyclCtt;
        }

        public void setIntPayCyclCtt(String intPayCyclCtt) {
            this.intPayCyclCtt = intPayCyclCtt;
        }

        public String getBondIntTcdNm() {
            return bondIntTcdNm;
        }

        public void setBondIntTcdNm(String bondIntTcdNm) {
            this.bondIntTcdNm = bondIntTcdNm;
        }

        public String getScrsItmsKcdNm() {
            return scrsItmsKcdNm;
        }

        public void setScrsItmsKcdNm(String scrsItmsKcdNm) {
            this.scrsItmsKcdNm = scrsItmsKcdNm;
        }

        public String getNiceScrsItmsKcdNm() {
            return niceScrsItmsKcdNm;
        }

        public void setNiceScrsItmsKcdNm(String niceScrsItmsKcdNm) {
            this.niceScrsItmsKcdNm = niceScrsItmsKcdNm;
        }

        public String getKisScrsItmsKcdNm() {
            return kisScrsItmsKcdNm;
        }

        public void setKisScrsItmsKcdNm(String kisScrsItmsKcdNm) {
            this.kisScrsItmsKcdNm = kisScrsItmsKcdNm;
        }

        public String getKbpScrsItmsKcdNm() {
            return kbpScrsItmsKcdNm;
        }

        public void setKbpScrsItmsKcdNm(String kbpScrsItmsKcdNm) {
            this.kbpScrsItmsKcdNm = kbpScrsItmsKcdNm;
        }

        public String getGrnDcdNm() {
            return grnDcdNm;
        }

        public void setGrnDcdNm(String grnDcdNm) {
            this.grnDcdNm = grnDcdNm;
        }

        public String getPamtRdptMcdNm() {
            return pamtRdptMcdNm;
        }

        public void setPamtRdptMcdNm(String pamtRdptMcdNm) {
            this.pamtRdptMcdNm = pamtRdptMcdNm;
        }
    }
}
