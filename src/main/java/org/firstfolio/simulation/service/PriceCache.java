package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.ProductPrice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장중 실시간 가격을 담아 두는 메모리 캐시 (2026-08-07 확정).
 *
 * <p>장중에는 2초마다 시세를 받는데, 매번 {@code product_prices}에 쓰면 <b>월 720MB</b>가 쌓인다.
 * 장중 가격 이력을 DB에 계속 쌓을 필요가 없으므로 메모리로만 돌고, 마감 뒤 확정 OHLCV 일봉만
 * 별도 테이블에 저장한다.</p>
 *
 * <h3>비어 있어도 정상이다</h3>
 *
 * <p>앱이 막 뜬 직후나 장외에는 이 캐시가 비어 있다. 그때는 {@link CurrentPriceReader}가
 * DB에 저장된 확정 일봉 종가로 넘어간다 — <b>캐시는 DB를 대체하는 것이 아니라 앞에 놓이는 것</b>이다.
 * 그래서 여기서 값을 못 찾는 것은 오류가 아니다.</p>
 *
 * <h3>만료를 두지 않는다</h3>
 *
 * <p>넣은 값은 다음 갱신까지 그대로 남는다. 마감 후에도 마지막 체결가가 유지되는데, 이것이
 * FUNC-036의 "마지막 유효 가격"과 맞다. 만료를 두면 장 마감 몇 시간 뒤부터 가격이 사라져
 * 평가가 <b>더 낡은</b> DB 값으로 되돌아간다 — 나아지는 것이 없다.</p>
 *
 * <h3>여러 스레드가 함께 쓴다</h3>
 *
 * <p>스케줄러 스레드가 쓰고, 요청 스레드들이 읽는다. {@link ConcurrentHashMap}이라
 * 별도 잠금이 필요 없다.</p>
 *
 * <p><b>넣은 뒤에는 그 {@link ProductPrice}를 고치지 않는다.</b> 이 클래스는 방어 복사를 하지
 * 않으므로, 넣은 객체를 나중에 수정하면 읽는 쪽에 그대로 보인다. 갱신은 새 객체를 만들어
 * 다시 넣는 방식으로만 한다.</p>
 */
@Component
public class PriceCache {

    private final ConcurrentHashMap<Long, ProductPrice> prices = new ConcurrentHashMap<>();

    /**
     * 한 상품의 가격을 넣는다. 같은 상품이 이미 있으면 덮어쓴다.
     *
     * <p>{@code productId}가 없는 값은 넣지 않는다 — 키를 만들 수 없다.</p>
     */
    public void put(ProductPrice price) {
        if (price == null || price.getProductId() == null) {
            return;
        }

        prices.put(price.getProductId(), price);
    }

    /** 한 번의 폴링으로 받은 값들을 한꺼번에 넣는다. */
    public void putAll(Collection<ProductPrice> newPrices) {
        if (newPrices == null) {
            return;
        }

        for (ProductPrice price : newPrices) {
            put(price);
        }
    }

    /** 캐시된 가격. <b>없으면 null이다</b> — 호출한 쪽이 DB로 넘어가야 한다. */
    public ProductPrice find(Long productId) {
        return productId == null ? null : prices.get(productId);
    }

    /**
     * 여러 상품의 캐시된 가격. <b>캐시에 없는 상품은 결과에서 빠진다.</b>
     *
     * <p>돌려주는 맵은 호출한 쪽의 것이라 마음대로 고쳐도 캐시에 영향이 없다.</p>
     */
    public Map<Long, ProductPrice> findAll(Collection<Long> productIds) {
        Map<Long, ProductPrice> found = new HashMap<>();

        if (productIds == null) {
            return found;
        }

        for (Long productId : productIds) {
            ProductPrice price = find(productId);

            if (price != null) {
                found.put(productId, price);
            }
        }

        return found;
    }

    /** 캐시된 상품 수. 폴링이 실제로 돌고 있는지 로그·점검으로 확인할 때 쓴다. */
    public int size() {
        return prices.size();
    }

    /**
     * 지금 캐시에 든 전부. <b>점검용이다.</b>
     *
     * <p>정상 폴링은 로그를 남기지 않아서(2초 주기라 하루 11,700줄이 된다) 캐시가 채워졌는지
     * 밖에서 볼 방법이 없었다. 내부 점검 API가 이 값을 쓴다.</p>
     *
     * <p>돌려주는 목록은 호출한 쪽의 것이고, 담긴 {@link ProductPrice}는 캐시가 들고 있는 것과
     * 같은 객체다 — <b>고치지 않는다.</b></p>
     */
    public List<ProductPrice> snapshot() {
        return new ArrayList<>(prices.values());
    }
}
