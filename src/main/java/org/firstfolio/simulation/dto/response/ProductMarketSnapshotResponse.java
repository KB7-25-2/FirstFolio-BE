package org.firstfolio.simulation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.simulation.domain.IntradayCandle;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.firstfolio.simulation.domain.ProductPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "2초 폴링용 현재가와 당일 OHLC 스냅샷")
public final class ProductMarketSnapshotResponse {

    private final Long productId;
    private final BigDecimal currentPrice;
    private final LocalDateTime priceReferenceAt;
    private final boolean marketOpen;
    private final Candle currentCandle;

    public ProductMarketSnapshotResponse(
            Long productId,
            ProductPrice currentPrice,
            boolean marketOpen,
            Candle currentCandle
    ) {
        this.productId = productId;
        this.currentPrice = currentPrice == null ? null : currentPrice.getPrice();
        this.priceReferenceAt = currentPrice == null ? null : currentPrice.getReferenceAt();
        this.marketOpen = marketOpen;
        this.currentCandle = currentCandle;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public LocalDateTime getPriceReferenceAt() {
        return priceReferenceAt;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public Candle getCurrentCandle() {
        return currentCandle;
    }

    public static Candle provisional(IntradayCandle candle) {
        return candle == null ? null : new Candle(
                candle.getTradeDate(),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                "PROVISIONAL",
                candle.getReferenceAt()
        );
    }

    public static Candle confirmed(IntradayCandle candle) {
        return candle == null ? null : new Candle(
                candle.getTradeDate(),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                "CONFIRMED",
                candle.getReferenceAt()
        );
    }

    public static Candle confirmed(ProductDailyCandle candle, LocalDateTime referenceAt) {
        return candle == null ? null : new Candle(
                candle.getTradeDate(),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                "CONFIRMED",
                referenceAt
        );
    }

    public static final class Candle {

        private final LocalDate tradeDate;
        private final BigDecimal openPrice;
        private final BigDecimal highPrice;
        private final BigDecimal lowPrice;
        private final BigDecimal closePrice;
        private final String status;
        private final LocalDateTime referenceAt;

        private Candle(
                LocalDate tradeDate,
                BigDecimal openPrice,
                BigDecimal highPrice,
                BigDecimal lowPrice,
                BigDecimal closePrice,
                String status,
                LocalDateTime referenceAt
        ) {
            this.tradeDate = tradeDate;
            this.openPrice = openPrice;
            this.highPrice = highPrice;
            this.lowPrice = lowPrice;
            this.closePrice = closePrice;
            this.status = status;
            this.referenceAt = referenceAt;
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

        public String getStatus() {
            return status;
        }

        public LocalDateTime getReferenceAt() {
            return referenceAt;
        }
    }
}
