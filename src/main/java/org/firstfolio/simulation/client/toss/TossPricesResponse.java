package org.firstfolio.simulation.client.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * 토스증권 {@code GET /api/v1/prices} 응답.
 *
 * <p>필드명이 camelCase({@code lastPrice})다. 한 번에 최대 200종목을 조회할 수 있어
 * 10종목이 요청 1건으로 끝난다.</p>
 *
 * <p><b>존재하지 않는 종목코드는 오류 없이 결과에서 빠진다.</b> 요청한 수와 받은 수를
 * 비교하지 않으면 조용히 누락된다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPricesResponse {

    private List<Item> result;

    public List<Item> getResult() {
        return result;
    }

    public void setResult(List<Item> result) {
        this.result = result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        private String symbol;

        /** {@code 2026-08-04T17:43:44.000+09:00} 형태. 오프셋이 붙는다. */
        private String timestamp;

        /** 숫자가 아니라 문자열로 온다. 금액이므로 BigDecimal로 받는다. */
        private BigDecimal lastPrice;

        private String currency;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public BigDecimal getLastPrice() {
            return lastPrice;
        }

        public void setLastPrice(BigDecimal lastPrice) {
            this.lastPrice = lastPrice;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }
}
