package org.firstfolio.simulation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 장중 현재가로 계속 갱신하는 메모리 전용 일봉. */
public final class IntradayCandle {

    private final Long productId;
    private final LocalDate tradeDate;
    private final BigDecimal openPrice;
    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final BigDecimal closePrice;
    private final BigDecimal volume;
    private final String currency;
    private final LocalDateTime referenceAt;

    public IntradayCandle(
            Long productId,
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal volume,
            String currency,
            LocalDateTime referenceAt
    ) {
        this.productId = productId;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.currency = currency;
        this.referenceAt = referenceAt;
    }

    public Long getProductId() {
        return productId;
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

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }
}
