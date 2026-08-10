package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.ProductPrice;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장중 실시간 가격을 담아 두는 메모리 캐시 (2026-08-07 확정).
 *
 * <p>장중에는 2초마다 시세를 받는데, 매번 {@code product_prices}에 쓰면 <b>월 720MB</b>가 쌓인다.
 * 그런데 과거 가격을 읽는 코드가 하나도 없다 — 전부 "가장 최근 1건"만 본다. 아무도 안 읽는
 * 데이터를 쌓고 그걸 지우는 배치를 또 만드는 셈이라, <b>장중에는 메모리로만 돌고 종가만 저장</b>한다.</p>
 *
 * <h3>비어 있어도 정상이다</h3>
 *
 * <p>앱이 막 뜬 직후나 장외에는 이 캐시가 비어 있다. 그때는 {@link CurrentPriceReader}가
 * DB에 저장된 종가로 넘어간다 — <b>캐시는 DB를 대체하는 것이 아니라 앞에 놓이는 것</b>이다.
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
}
