package org.firstfolio.simulation.domain;

/**
 * 주식의 실제 조건. {@code financial_products.real_terms_json}에 저장한다.
 *
 * <p>주식에는 금리도 만기도 없다. 대신 <b>시장 구분과 산업군</b>이 상품을 이해하는 축이다.
 * 시장 구분을 여기에 담는 것은 SIMULATION_POLICY_v3 2.2절이 명시한다.</p>
 *
 * <p><b>실제 종목명·종목코드는 담지 않는다.</b> {@code real_terms}는 사용자 응답에 그대로
 * 실리므로 원상품을 특정할 수 있는 값이 들어가면 안 된다 (FUNC-032). 산업군은 분산투자를
 * 배우는 데 필요한 분류라서 포함한다.</p>
 */
public class StockRealTerms {

    /** KOSPI 또는 KOSDAQ. 증권거래세율은 같지만 상품 메타데이터로 보관한다 (v3 2.2절). */
    private String market;

    /** 반도체, 방산, 제약, 엔터, 조선. */
    private String sector;

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }
}
