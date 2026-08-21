package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.domain.ProductDailyCandle;
import org.firstfolio.simulation.mapper.ProductDailyCandleMapper;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 체결·평가·조회에 쓰는 <b>현재가를 읽는 단 하나의 자리</b>.
 *
 * <p>주문마다 외부 API를 부르지 않는 이유는 Rate Limit·제공처 장애·IP 화이트리스트에
 * <b>거래 기능 전체가 묶이기 때문</b>이다. v3 3.2절의 "가격 확정 시점: 주문 시점"은
 * <i>그 시점의 값으로 금액을 확정한다</i>는 뜻이지 <i>그때 외부를 호출하라</i>는 뜻이 아니다.</p>
 *
 * <h3>캐시 → 확정 일봉 → 이전 가격 이력</h3>
 *
 * <p>장중 실시간 가격은 {@link PriceCache}에 있고, 확정 종가는 일봉 테이블에 저장된다.
 * 캐시가 비는 앱 시작 직후·장외에는 최신 확정 일봉 종가를 사용한다. 전환 전에 저장된
 * {@code product_prices}는 그마저 없을 때만 읽는 마지막 호환 경로다.</p>
 *
 * <h3>가격을 읽는 곳을 여기로 모은다</h3>
 *
 * <p>체결가·평가액·상품 상세의 현재가가 <b>서로 다른 자리에서 읽으면 값이 갈라진다.</b>
 * 화면에는 종가가 보이는데 체결은 실시간가로 되는 식이다. 한 자리로 모아 두면 캐시 정책이
 * 바뀌어도 이 클래스 안만 고치면 된다.</p>
 */
@Component
public class CurrentPriceReader {

    private final PriceCache priceCache;
    private final ProductDailyCandleMapper candleMapper;
    private final ProductPriceMapper productPriceMapper;
    private final TradingHours tradingHours;

    @Autowired
    public CurrentPriceReader(
            PriceCache priceCache,
            ProductDailyCandleMapper candleMapper,
            ProductPriceMapper productPriceMapper,
            TradingHours tradingHours
    ) {
        this.priceCache = priceCache;
        this.candleMapper = candleMapper;
        this.productPriceMapper = productPriceMapper;
        this.tradingHours = tradingHours;
    }

    /** 기존 단위 테스트·점진 전환용 생성자. 운영 빈은 위 생성자를 사용한다. */
    public CurrentPriceReader(PriceCache priceCache, ProductPriceMapper productPriceMapper) {
        this.priceCache = priceCache;
        this.candleMapper = null;
        this.productPriceMapper = productPriceMapper;
        this.tradingHours = new TradingHours();
    }

    /**
     * 마지막 유효 기준 가격. <b>캐시에도 DB에도 없으면 null이다.</b>
     *
     * <p>없는 가격을 만들어 내지 않는다 (FUNC-036). 거래는 이 경우 거부해야 한다 —
     * 임의 값으로 체결하면 사용자 자산이 사실과 달라진다.</p>
     */
    public ProductPrice read(Long productId) {
        ProductPrice cached = priceCache.find(productId);

        if (cached != null) {
            return cached;
        }

        if (candleMapper != null) {
            ProductDailyCandle candle = candleMapper.findLatestByProductId(productId);

            if (candle != null) {
                return toPrice(candle);
            }
        }

        // 배포 전 이미 저장된 종가가 사라지지 않도록 product_prices를 마지막 호환 경로로 둔다.
        return productPriceMapper.findLatestByProductId(productId);
    }

    /**
     * 여러 상품의 마지막 유효 기준 가격. <b>어디에도 없는 상품은 결과에서 빠진다.</b>
     *
     * <p>포트폴리오 평가는 보유 상품 수만큼 가격이 필요하다. 상품마다 {@link #read}를 부르면
     * 캐시에 없는 만큼 질의가 늘어나므로, <b>캐시에서 빠진 것만 모아 DB를 한 번</b> 부른다.</p>
     *
     * <p>전부 캐시에 있으면 DB를 아예 부르지 않는다. 장중에는 이쪽이 정상이다.</p>
     */
    public Map<Long, ProductPrice> readAll(Collection<Long> productIds) {
        Map<Long, ProductPrice> found = priceCache.findAll(productIds);

        if (productIds == null) {
            return found;
        }

        // 같은 상품이 두 번 들어와도 질의는 한 번이면 된다.
        Set<Long> missing = new LinkedHashSet<>();

        for (Long productId : productIds) {
            if (productId != null && !found.containsKey(productId)) {
                missing.add(productId);
            }
        }

        if (missing.isEmpty()) {
            return found;
        }

        Map<Long, ProductPrice> merged = new HashMap<>(found);

        if (candleMapper != null) {
            for (ProductDailyCandle candle : candleMapper.findLatestByProductIds(new ArrayList<>(missing))) {
                merged.put(candle.getProductId(), toPrice(candle));
                missing.remove(candle.getProductId());
            }
        }

        if (missing.isEmpty()) {
            return merged;
        }

        for (ProductPrice price : productPriceMapper.findLatestByProductIds(new ArrayList<>(missing))) {
            merged.put(price.getProductId(), price);
        }

        return merged;
    }

    private ProductPrice toPrice(ProductDailyCandle candle) {
        ProductPrice price = new ProductPrice();

        price.setProductId(candle.getProductId());
        price.setPrice(candle.getClosePrice());
        price.setReferenceAt(tradingHours.closeAtUtc(candle.getTradeDate()));
        price.setSourceType(candle.getSourceType());

        return price;
    }
}
