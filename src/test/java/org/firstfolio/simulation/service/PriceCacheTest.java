package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.ProductPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceCacheTest {

    private final PriceCache cache = new PriceCache();

    private static ProductPrice price(Long productId, String value) {
        ProductPrice price = new ProductPrice();

        price.setProductId(productId);
        price.setPrice(new BigDecimal(value));

        return price;
    }

    @Test
    @DisplayName("넣은 가격을 그대로 돌려준다")
    void returnsWhatWasPut() {
        cache.put(price(87L, "241500.0000"));

        assertEquals(new BigDecimal("241500.0000"), cache.find(87L).getPrice());
    }

    @Test
    @DisplayName("같은 상품을 다시 넣으면 덮어쓴다 — 폴링마다 최신 값만 남는다")
    void overwritesOnRepeatedPut() {
        cache.put(price(87L, "241500.0000"));
        cache.put(price(87L, "242000.0000"));

        assertEquals(new BigDecimal("242000.0000"), cache.find(87L).getPrice());
        assertEquals(1, cache.size(), "덮어쓰기이므로 항목이 늘지 않습니다.");
    }

    @Test
    @DisplayName("없는 상품은 null이다 — 오류가 아니라 DB로 넘어가라는 신호다")
    void returnsNullWhenAbsent() {
        assertNull(cache.find(87L));
    }

    @Test
    @DisplayName("productId가 없는 값은 넣지 않는다 — 키를 만들 수 없다")
    void ignoresPriceWithoutProductId() {
        cache.put(price(null, "241500.0000"));
        cache.put(null);

        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("한 번의 폴링 결과를 한꺼번에 넣는다")
    void putsAllAtOnce() {
        cache.putAll(List.of(price(87L, "241500.0000"), price(88L, "72300.0000")));

        assertEquals(2, cache.size());
        assertNotNull(cache.find(87L));
        assertNotNull(cache.find(88L));
    }

    @Test
    @DisplayName("null 입력에도 터지지 않는다")
    void toleratesNullInput() {
        cache.putAll(null);

        assertEquals(0, cache.size());
        assertNull(cache.find(null));
        assertTrue(cache.findAll(null).isEmpty());
    }

    @Test
    @DisplayName("여러 상품을 한 번에 찾되, 캐시에 없는 것은 결과에서 빠진다")
    void findsOnlyWhatIsCached() {
        cache.put(price(87L, "241500.0000"));

        Map<Long, ProductPrice> found = cache.findAll(List.of(87L, 88L));

        assertEquals(1, found.size());
        assertTrue(found.containsKey(87L));
        assertTrue(!found.containsKey(88L), "88은 캐시에 없으므로 빠져야 합니다.");
    }

    @Test
    @DisplayName("돌려준 맵을 고쳐도 캐시는 그대로다")
    void returnedMapIsDetached() {
        cache.put(price(87L, "241500.0000"));

        Map<Long, ProductPrice> found = cache.findAll(List.of(87L));
        found.clear();

        assertNotNull(cache.find(87L), "호출한 쪽이 맵을 비워도 캐시가 지워지면 안 됩니다.");
    }

    @Test
    @DisplayName("여러 스레드가 동시에 쓰고 읽어도 값이 깨지지 않는다")
    void survivesConcurrentAccess() throws Exception {
        int threads = 8;
        int perThread = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> productIds = List.of(87L, 88L, 89L);
        List<Throwable> failures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();

                    for (int i = 0; i < perThread; i++) {
                        // 스케줄러 스레드가 쓰는 동안 요청 스레드들이 읽는 상황을 흉내 낸다.
                        cache.putAll(List.of(
                                price(87L, "241500.0000"),
                                price(88L, "72300.0000"),
                                price(89L, "15400.0000")
                        ));
                        cache.findAll(productIds);
                    }
                } catch (Throwable failure) {
                    synchronized (failures) {
                        failures.add(failure);
                    }
                }
            });
        }

        start.countDown();
        pool.shutdown();

        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "작업이 제때 끝나지 않았습니다.");
        assertEquals(List.of(), failures, "동시 접근에서 예외가 났습니다: " + failures);
        assertEquals(3, cache.size(), "상품 3개만 있어야 합니다.");

        for (Long productId : productIds) {
            assertNotNull(cache.find(productId));
        }
    }

    @Test
    @DisplayName("만료가 없다 — 넣어 둔 값은 다음 갱신까지 남는다")
    void keepsValueUntilNextUpdate() {
        // 장 마감 후에도 마지막 체결가로 평가·거래가 이어져야 한다 (FUNC-036 마지막 유효 가격).
        cache.putAll(Arrays.asList(price(87L, "241500.0000")));

        assertNotNull(cache.find(87L));
        assertEquals(1, cache.size());
    }
}
