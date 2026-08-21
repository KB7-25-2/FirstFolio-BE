package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.IntradayCandle;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.dto.response.ProductCandleHistoryResponse;
import org.firstfolio.simulation.dto.response.ProductMarketSnapshotResponse;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.mapper.ProductDailyCandleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 사용자용 차트 이력과 가벼운 현재 시장 스냅샷 조회. */
@Service
public class ProductMarketDataQueryService {

    private static final int DEFAULT_CANDLE_COUNT = 200;
    private static final int MAX_CANDLE_COUNT = 200;
    private static final List<AssetType> SUPPORTED = List.of(AssetType.STOCK, AssetType.FUND);

    private final FinancialProductMapper productMapper;
    private final ProductDailyCandleMapper candleMapper;
    private final CurrentPriceReader currentPriceReader;
    private final IntradayCandleCache intradayCandleCache;
    private final TradingHours tradingHours;
    private final Clock clock;

    public ProductMarketDataQueryService(
            FinancialProductMapper productMapper,
            ProductDailyCandleMapper candleMapper,
            CurrentPriceReader currentPriceReader,
            IntradayCandleCache intradayCandleCache,
            TradingHours tradingHours,
            Clock clock
    ) {
        this.productMapper = productMapper;
        this.candleMapper = candleMapper;
        this.currentPriceReader = currentPriceReader;
        this.intradayCandleCache = intradayCandleCache;
        this.tradingHours = tradingHours;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProductCandleHistoryResponse findCandles(Long productId, Integer count) {
        requireSupportedProduct(productId);
        int resolvedCount = resolveCount(count);

        return new ProductCandleHistoryResponse(
                productId,
                candleMapper.findRecentByProductId(productId, resolvedCount)
        );
    }

    @Transactional(readOnly = true)
    public ProductMarketSnapshotResponse findMarketSnapshot(Long productId) {
        requireSupportedProduct(productId);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = tradingHours.koreaDate(now);
        boolean marketOpen = tradingHours.isMarketOpen(now);
        ProductPrice currentPrice = currentPriceReader.read(productId);
        IntradayCandle cached = intradayCandleCache.find(productId);
        ProductMarketSnapshotResponse.Candle candle;

        if (cached != null) {
            boolean provisional = marketOpen && today.equals(cached.getTradeDate());
            candle = provisional
                    ? ProductMarketSnapshotResponse.provisional(cached)
                    : ProductMarketSnapshotResponse.confirmed(cached);
        } else {
            ProductDailyCandle confirmed = candleMapper.findLatestByProductId(productId);
            candle = ProductMarketSnapshotResponse.confirmed(
                    confirmed,
                    confirmed == null ? null : tradingHours.closeAtUtc(confirmed.getTradeDate())
            );
        }

        return new ProductMarketSnapshotResponse(productId, currentPrice, marketOpen, candle);
    }

    private FinancialProduct requireSupportedProduct(Long productId) {
        FinancialProduct product = productMapper.findActiveById(productId);

        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (!SUPPORTED.contains(product.getAssetType())) {
            throw new ApiException(ErrorCode.PRODUCT_CANDLE_NOT_SUPPORTED);
        }

        return product;
    }

    private static int resolveCount(Integer count) {
        if (count == null) {
            return DEFAULT_CANDLE_COUNT;
        }

        if (count < 1 || count > MAX_CANDLE_COUNT) {
            throw new ApiException(ErrorCode.INVALID_CANDLE_QUERY);
        }

        return count;
    }
}
