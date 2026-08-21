package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.simulation.client.toss.TossCandlesResponse;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.firstfolio.simulation.mapper.ProductDailyCandleMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 토스 일봉을 확정 이력 DB와 장중 메모리 캐시에 동기화한다. */
@Service
public class ProductCandleSyncService {

    private static final Logger log = LogManager.getLogger(ProductCandleSyncService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String SOURCE_TYPE = "TOSS_INVEST";
    private static final int PRICE_SCALE = 4;
    private static final int VOLUME_SCALE = 8;

    private final PriceQuoteFetcher quoteFetcher;
    private final TossInvestClient tossInvestClient;
    private final ProductDailyCandleMapper candleMapper;
    private final IntradayCandleCache intradayCandleCache;
    private final TradingHours tradingHours;

    public ProductCandleSyncService(
            PriceQuoteFetcher quoteFetcher,
            TossInvestClient tossInvestClient,
            ProductDailyCandleMapper candleMapper,
            IntradayCandleCache intradayCandleCache,
            TradingHours tradingHours
    ) {
        this.quoteFetcher = quoteFetcher;
        this.tossInvestClient = tossInvestClient;
        this.candleMapper = candleMapper;
        this.intradayCandleCache = intradayCandleCache;
        this.tradingHours = tradingHours;
    }

    /** 아직 일봉이 전혀 없는 공개 주식·ETF만 최신 200봉으로 초기 적재한다. */
    public CandleSyncResult bootstrapMissing(LocalDateTime nowUtc) {
        List<FinancialProduct> targets = quoteFetcher.findTargets(null);

        if (targets.isEmpty()) {
            return new CandleSyncResult(0, 0, 0);
        }

        List<Long> productIds = targets.stream().map(FinancialProduct::getProductId).toList();
        Set<Long> existing = new HashSet<>(candleMapper.findProductIdsWithCandles(productIds));
        List<FinancialProduct> missing = targets.stream()
                .filter(product -> !existing.contains(product.getProductId()))
                .toList();

        return sync(missing, 200, true, nowUtc);
    }

    /** 장 마감 후 최근 5봉을 다시 받아 오늘 확정 봉과 최근 교정분을 upsert한다. */
    public CandleSyncResult syncRecentConfirmed(LocalDateTime nowUtc) {
        return sync(quoteFetcher.findTargets(null), 5, true, nowUtc);
    }

    /** 수정주가 재산정에 대비해 최근 200봉 전체를 다시 맞춘다. */
    public CandleSyncResult reconcileAll(LocalDateTime nowUtc) {
        return sync(quoteFetcher.findTargets(null), 200, true, nowUtc);
    }

    /** 장중 공식 일봉으로 메모리 OHLCV만 교정한다. DB에는 쓰지 않는다. */
    public CandleSyncResult refreshIntraday(LocalDateTime nowUtc) {
        return sync(quoteFetcher.findTargets(null), 1, false, nowUtc);
    }

    private CandleSyncResult sync(
            List<FinancialProduct> targets,
            int count,
            boolean persistConfirmed,
            LocalDateTime nowUtc
    ) {
        int saved = 0;
        int failed = 0;

        for (FinancialProduct product : targets) {
            String symbol = product.getSourceProductCode();

            if (symbol == null || symbol.isBlank()) {
                failed++;
                log.warn("종목코드가 없어 일봉을 동기화할 수 없습니다 productId={}", product.getProductId());
                continue;
            }

            try {
                List<ProductDailyCandle> converted = convert(
                        product,
                        tossInvestClient.fetchDailyCandles(symbol.trim(), count),
                        nowUtc
                );

                if (converted.isEmpty()) {
                    failed++;
                    log.warn("일봉 응답이 비어 있습니다 productId={} symbol={}",
                            product.getProductId(), symbol);
                    continue;
                }

                // 응답은 최신순이다. 첫 유효 봉으로 장중 메모리 값을 교정한다.
                intradayCandleCache.seed(converted.get(0));

                if (!persistConfirmed) {
                    continue;
                }

                List<ProductDailyCandle> confirmed = converted.stream()
                        .filter(candle -> isConfirmed(candle.getTradeDate(), nowUtc))
                        .toList();

                if (!confirmed.isEmpty()) {
                    candleMapper.upsertAll(confirmed);
                    saved += confirmed.size();
                }
            } catch (Exception exception) {
                failed++;
                log.warn(
                        "상품 일봉 동기화에 실패했습니다 productId={} symbol={}",
                        product.getProductId(),
                        symbol,
                        exception
                );
            }
        }

        return new CandleSyncResult(targets.size(), saved, failed);
    }

    private List<ProductDailyCandle> convert(
            FinancialProduct product,
            List<TossCandlesResponse.Item> items,
            LocalDateTime nowUtc
    ) {
        List<ProductDailyCandle> converted = new ArrayList<>();

        for (TossCandlesResponse.Item item : items) {
            ProductDailyCandle candle = convert(product, item, nowUtc);

            if (candle != null) {
                converted.add(candle);
            }
        }

        return converted;
    }

    private ProductDailyCandle convert(
            FinancialProduct product,
            TossCandlesResponse.Item item,
            LocalDateTime nowUtc
    ) {
        OffsetDateTime timestamp;

        try {
            timestamp = OffsetDateTime.parse(item.getTimestamp());
        } catch (Exception exception) {
            log.warn("캔들 기준 시각이 올바르지 않습니다 productId={} timestamp={}",
                    product.getProductId(), item.getTimestamp());
            return null;
        }

        BigDecimal open = price(item.getOpenPrice());
        BigDecimal high = price(item.getHighPrice());
        BigDecimal low = price(item.getLowPrice());
        BigDecimal close = price(item.getClosePrice());
        BigDecimal volume = volume(item.getVolume());

        if (!valid(open, high, low, close, volume)) {
            log.warn("캔들 값이 올바르지 않습니다 productId={} timestamp={}",
                    product.getProductId(), item.getTimestamp());
            return null;
        }

        ProductDailyCandle candle = new ProductDailyCandle();

        candle.setProductId(product.getProductId());
        candle.setTradeDate(timestamp.atZoneSameInstant(KOREA).toLocalDate());
        candle.setOpenPrice(open);
        candle.setHighPrice(high);
        candle.setLowPrice(low);
        candle.setClosePrice(close);
        candle.setVolume(volume);
        candle.setCurrency(item.getCurrency());
        candle.setAdjusted(true);
        candle.setSourceType(SOURCE_TYPE);
        candle.setSourceReferenceAt(
                timestamp.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        );
        candle.setCreatedAt(nowUtc);
        candle.setUpdatedAt(nowUtc);

        return candle;
    }

    private boolean isConfirmed(LocalDate tradeDate, LocalDateTime nowUtc) {
        LocalDate today = tradingHours.koreaDate(nowUtc);

        return tradeDate.isBefore(today)
                || (tradeDate.equals(today) && tradingHours.isAfterClose(nowUtc));
    }

    private static BigDecimal price(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return null;
        }

        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal volume(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return null;
        }

        return value.setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean valid(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {
        return open != null
                && high != null
                && low != null
                && close != null
                && volume != null
                && high.compareTo(open) >= 0
                && high.compareTo(close) >= 0
                && low.compareTo(open) <= 0
                && low.compareTo(close) <= 0;
    }
}
