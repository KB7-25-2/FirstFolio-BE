package org.firstfolio.simulation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.simulation.domain.ProductDailyCandle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "가명 모의 상품의 확정 일봉 이력")
public final class ProductCandleHistoryResponse {

    private final Long productId;
    private final String interval;
    private final List<Item> candles;

    public ProductCandleHistoryResponse(Long productId, List<ProductDailyCandle> candles) {
        this.productId = productId;
        this.interval = "1d";
        this.candles = candles.stream().map(Item::new).toList();
    }

    public Long getProductId() {
        return productId;
    }

    public String getInterval() {
        return interval;
    }

    public List<Item> getCandles() {
        return candles;
    }

    public static final class Item {

        private final LocalDate tradeDate;
        private final BigDecimal openPrice;
        private final BigDecimal highPrice;
        private final BigDecimal lowPrice;
        private final BigDecimal closePrice;
        private final BigDecimal volume;
        private final String currency;

        private Item(ProductDailyCandle candle) {
            this.tradeDate = candle.getTradeDate();
            this.openPrice = candle.getOpenPrice();
            this.highPrice = candle.getHighPrice();
            this.lowPrice = candle.getLowPrice();
            this.closePrice = candle.getClosePrice();
            this.volume = candle.getVolume();
            this.currency = candle.getCurrency();
        }

        public LocalDate getTradeDate() {
            return tradeDate;
        }

        public BigDecimal getOpenPrice() {
            return openPrice;
        }

        public BigDecimal getHighPrice() {
            return highPrice;
        }

        public BigDecimal getLowPrice() {
            return lowPrice;
        }

        public BigDecimal getClosePrice() {
            return closePrice;
        }

        public BigDecimal getVolume() {
            return volume;
        }

        public String getCurrency() {
            return currency;
        }
    }
}
