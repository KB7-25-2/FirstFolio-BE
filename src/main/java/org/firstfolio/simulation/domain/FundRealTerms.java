package org.firstfolio.simulation.domain;

/**
 * 펀드(ETF)의 실제 조건. {@code financial_products.real_terms_json}에 저장한다.
 *
 * <p>ETF에는 금리도 만기도 없다. 상품을 이해하는 축은 <b>무엇에 투자하는 펀드인가</b>
 * — 주식형인지 채권형인지 섞은 것인지다.</p>
 *
 * <p><b>기초지수명({@code bssIdxIdxNm})은 담지 않는다.</b> "코스피 200"이나
 * "KAP 한국종합채권지수" 같은 값은 원상품을 바로 특정할 수 있다 (FUNC-032).
 * 종목명·종목코드도 마찬가지다.</p>
 */
public class FundRealTerms {

    /** EQUITY(주식형), BOND(채권형), MIXED(혼합형). */
    private String fundType;

    public String getFundType() {
        return fundType;
    }

    public void setFundType(String fundType) {
        this.fundType = fundType;
    }
}
