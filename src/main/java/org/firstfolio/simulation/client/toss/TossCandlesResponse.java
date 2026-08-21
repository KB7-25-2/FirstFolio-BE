package org.firstfolio.simulation.client.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** 토스증권 {@code GET /api/v1/candles} 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossCandlesResponse {

    private Result result;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private List<Item> candles;
        private String nextBefore;

        public List<Item> getCandles() {
            return candles;
        }

        public void setCandles(List<Item> candles) {
            this.candles = candles;
        }

        public String getNextBefore() {
            return nextBefore;
        }

        public void setNextBefore(String nextBefore) {
            this.nextBefore = nextBefore;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        private String timestamp;
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal closePrice;
        private BigDecimal volume;
        private String currency;

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public BigDecimal getOpenPrice() {
            return openPrice;
        }

        public void setOpenPrice(BigDecimal openPrice) {
            this.openPrice = openPrice;
        }

        public BigDecimal getHighPrice() {
            return highPrice;
        }

        public void setHighPrice(BigDecimal highPrice) {
            this.highPrice = highPrice;
        }

        public BigDecimal getLowPrice() {
            return lowPrice;
        }

        public void setLowPrice(BigDecimal lowPrice) {
            this.lowPrice = lowPrice;
        }

        public BigDecimal getClosePrice() {
            return closePrice;
        }

        public void setClosePrice(BigDecimal closePrice) {
            this.closePrice = closePrice;
        }

        public BigDecimal getVolume() {
            return volume;
        }

        public void setVolume(BigDecimal volume) {
            this.volume = volume;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }
}
