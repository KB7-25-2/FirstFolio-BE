package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.IntradayCandle;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프론트가 2초마다 읽을 당일 OHLC를 보관한다. 현재가 폴링마다 메모리에서만 갱신한다.
 */
@Component
public class IntradayCandleCache {

    private final ConcurrentHashMap<Long, IntradayCandle> candles = new ConcurrentHashMap<>();

    /** 현재가 한 틱을 반영한다. 새로운 거래일이면 첫 가격으로 새 봉을 시작한다. */
    public void update(
            Long productId,
            LocalDate tradeDate,
            BigDecimal price,
            String currency,
            LocalDateTime referenceAt
    ) {
        if (productId == null || tradeDate == null || price == null || price.signum() <= 0) {
            return;
        }

        candles.compute(productId, (ignored, previous) -> {
            if (previous == null || !tradeDate.equals(previous.getTradeDate())) {
                return new IntradayCandle(
                        productId,
                        tradeDate,
                        price,
                        price,
                        price,
                        price,
                        BigDecimal.ZERO,
                        currency,
                        referenceAt
                );
            }

            return new IntradayCandle(
                    productId,
                    tradeDate,
                    previous.getOpenPrice(),
                    previous.getHighPrice().max(price),
                    previous.getLowPrice().min(price),
                    price,
                    previous.getVolume(),
                    currency == null ? previous.getCurrency() : currency,
                    referenceAt
            );
        });
    }

    /**
     * 토스의 당일 일봉으로 시가·누적 고저가·거래량을 교정한다.
     *
     * <p>동시에 더 최신 현재가 틱이 들어왔다면 그 틱의 종가와 기준 시각은 보존한다.</p>
     */
    public void seed(ProductDailyCandle authoritative) {
        if (authoritative == null
                || authoritative.getProductId() == null
                || authoritative.getTradeDate() == null) {
            return;
        }

        candles.compute(authoritative.getProductId(), (ignored, previous) -> {
            if (previous == null
                    || !authoritative.getTradeDate().equals(previous.getTradeDate())) {
                return from(authoritative);
            }

            boolean previousIsNewer = previous.getReferenceAt() != null
                    && authoritative.getUpdatedAt() != null
                    && previous.getReferenceAt().isAfter(authoritative.getUpdatedAt());

            return new IntradayCandle(
                    authoritative.getProductId(),
                    authoritative.getTradeDate(),
                    authoritative.getOpenPrice(),
                    authoritative.getHighPrice().max(previous.getHighPrice()),
                    authoritative.getLowPrice().min(previous.getLowPrice()),
                    previousIsNewer ? previous.getClosePrice() : authoritative.getClosePrice(),
                    authoritative.getVolume(),
                    authoritative.getCurrency(),
                    previousIsNewer ? previous.getReferenceAt() : authoritative.getUpdatedAt()
            );
        });
    }

    public IntradayCandle find(Long productId) {
        return productId == null ? null : candles.get(productId);
    }

    private static IntradayCandle from(ProductDailyCandle candle) {
        return new IntradayCandle(
                candle.getProductId(),
                candle.getTradeDate(),
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice(),
                candle.getVolume(),
                candle.getCurrency(),
                candle.getUpdatedAt()
        );
    }
}
