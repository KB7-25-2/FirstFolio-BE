package org.firstfolio.persistence;

import org.firstfolio.config.RootConfig;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.firstfolio.simulation.service.PriceQuoteFetcher;
import org.firstfolio.simulation.service.PriceRefreshResult;
import org.firstfolio.simulation.service.PriceRefreshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 가격 갱신을 실제 MySQL에 붙여서 확인한다 (FUNC-040).
 *
 * <p>서비스 테스트는 매퍼를 모킹하므로 <b>INSERT 문을 한 번도 실행하지 않는다.</b>
 * 유니크 제약이 실제로 멱등성을 보장하는지, 컬럼·타입이 맞는지는 여기서만 드러난다.</p>
 *
 * <p><b>토스 클라이언트는 대역으로 바꾼다.</b> 외부 응답 형식은 실호출로 이미 확인했고
 * (2026-08-06, {@code DECISION_ETF_PRICE_SOURCE_20260806.md}) 파싱은
 * {@code TossPricesParsingTest}가 실제 응답 샘플로 지킨다. 여기서 확인할 것은
 * <b>DB에 무슨 일이 일어나는가</b>이므로, 매번 외부 시세에 결과가 흔들리면 오히려 검증이 흐려진다.</p>
 *
 * <p>넣은 데이터는 <b>테스트 끝에 전부 롤백</b>한다.</p>
 *
 * <pre>./gradlew jdbcTest</pre>
 */
@Tag("jdbc")
class PriceRefreshJdbcTest {

    private static final BigDecimal FAKE_PRICE = new BigDecimal("123450");
    private static final String QUOTED_AT = "2026-08-06T13:19:15.000+09:00";

    @Test
    @DisplayName("실제 DB에 공개 주식·펀드 가격을 쌓고, 다시 돌려도 중복되지 않는다")
    void refreshesPricesAndStaysIdempotent() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper productMapper = context.getBean(FinancialProductMapper.class);
            ProductPriceMapper priceMapper = context.getBean(ProductPriceMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                List<FinancialProduct> targets = productMapper.findPriceTargets(
                        List.of(AssetType.STOCK, AssetType.FUND),
                        null
                );

                assertFalse(targets.isEmpty(), "공개된 주식·펀드가 없습니다. 상품 시드를 먼저 등록하세요.");

                PriceRefreshService service = new PriceRefreshService(
                        new PriceQuoteFetcher(productMapper, fakeClient(targets)),
                        priceMapper,
                        new BigDecimal("0.30")
                );

                LocalDateTime referenceAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

                // 1회차 — 전부 새로 저장된다.
                PriceRefreshResult first = service.refresh(referenceAt, null);

                assertEquals(targets.size(), first.getProcessedCount());
                assertEquals(targets.size(), first.getCreatedCount(), "모든 대상에 가격이 저장돼야 합니다.");
                assertEquals(0, first.getSkippedCount());

                // 2회차 — 같은 기준 시각이므로 유니크 제약에 걸려 전부 건너뛴다.
                PriceRefreshResult second = service.refresh(referenceAt, null);

                assertEquals(targets.size(), second.getProcessedCount());
                assertEquals(0, second.getCreatedCount(), "배치를 다시 돌려도 중복 저장되면 안 됩니다.");
                assertEquals(targets.size(), second.getSkippedCount());

                // 저장된 내용 확인 — 평가가 읽는 바로 그 경로로 되읽는다.
                List<Long> productIds = new ArrayList<>();

                for (FinancialProduct product : targets) {
                    productIds.add(product.getProductId());
                }

                Map<Long, ProductPrice> stored = new HashMap<>();

                for (ProductPrice price : priceMapper.findLatestByProductIds(productIds)) {
                    stored.put(price.getProductId(), price);
                }

                assertEquals(
                        targets.size(),
                        stored.size(),
                        "평가가 쓰는 조회 경로에서 모든 상품의 가격이 보여야 합니다."
                );

                FinancialProduct sample = targets.get(0);
                ProductPrice price = stored.get(sample.getProductId());

                assertNotNull(price);
                assertEquals(0, FAKE_PRICE.compareTo(price.getPrice()));
                assertEquals(referenceAt, price.getReferenceAt(), "기준 시각은 갱신 실행 시점입니다.");
                assertEquals("REAL_DATA", price.getSourceType());
                assertTrue(
                        price.getGenerationKey().startsWith("toss:" + sample.getSourceProductCode() + ":"),
                        "생성 키에 제공처와 종목코드가 남아야 합니다: " + price.getGenerationKey()
                );
                assertTrue(
                        price.getGenerationKey().endsWith("2026-08-06T04:19:15Z"),
                        "생성 키에 실제 체결 시각(UTC)이 남아야 합니다: " + price.getGenerationKey()
                );
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    @Test
    @DisplayName("새 체결이 있을 때만 가격이 쌓인다 — 같은 체결값은 기준 시각을 바꿔도 저장되지 않는다")
    void appendsRowOnlyWhenQuoteChanges() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            DataSourceTransactionManager transactionManager =
                    context.getBean(DataSourceTransactionManager.class);
            FinancialProductMapper productMapper = context.getBean(FinancialProductMapper.class);
            ProductPriceMapper priceMapper = context.getBean(ProductPriceMapper.class);

            TransactionStatus transaction =
                    transactionManager.getTransaction(new DefaultTransactionDefinition());

            try {
                List<FinancialProduct> targets = productMapper.findPriceTargets(
                        List.of(AssetType.STOCK),
                        null
                );

                assertFalse(targets.isEmpty(), "공개된 주식이 없습니다.");

                FinancialProduct sample = targets.get(0);
                List<Long> onlySample = List.of(sample.getProductId());

                // 둘 다 과거여야 한다. 미래 시각은 서비스가 거부한다.
                LocalDateTime first = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusMinutes(5);

                PriceRefreshService sameQuote = new PriceRefreshService(
                        new PriceQuoteFetcher(productMapper, fakeClient(targets, QUOTED_AT)),
                        priceMapper,
                        new BigDecimal("0.30")
                );

                assertEquals(1, sameQuote.refresh(first, onlySample).getCreatedCount());

                // 기준 시각은 달라졌지만 체결은 그대로다 — 저장하지 않는다.
                PriceRefreshResult unchanged = sameQuote.refresh(first.plusMinutes(1), onlySample);

                assertEquals(0, unchanged.getCreatedCount(), "같은 체결값을 또 쌓으면 안 됩니다.");
                assertEquals(1, unchanged.getSkippedCount());

                // 새 체결이 들어오면 그때 쌓인다.
                PriceRefreshService newQuote = new PriceRefreshService(
                        new PriceQuoteFetcher(
                                productMapper,
                                fakeClient(targets, "2026-08-06T13:25:40.000+09:00")
                        ),
                        priceMapper,
                        new BigDecimal("0.30")
                );
                LocalDateTime third = first.plusMinutes(2);

                assertEquals(1, newQuote.refresh(third, onlySample).getCreatedCount());

                ProductPrice latest = priceMapper.findLatestByProductId(sample.getProductId());

                assertEquals(third, latest.getReferenceAt(), "가장 최근 기준 시각의 가격이 조회돼야 합니다.");
                assertTrue(latest.getGenerationKey().endsWith("2026-08-06T04:25:40Z"));
            } finally {
                transactionManager.rollback(transaction);
            }
        }
    }

    /**
     * 대상 상품의 종목코드로 고정 가격을 돌려주는 대역.
     *
     * <p>실제 시세를 쓰면 값이 매번 달라 단언을 걸 수 없다. 확인하려는 것은 가격의 크기가 아니라
     * <b>DB에 어떻게 저장되는가</b>다.</p>
     */
    private static TossInvestClient fakeClient(List<FinancialProduct> targets) {
        return fakeClient(targets, QUOTED_AT);
    }

    private static TossInvestClient fakeClient(List<FinancialProduct> targets, String quotedAt) {
        List<TossPricesResponse.Item> quotes = new ArrayList<>();

        for (FinancialProduct product : targets) {
            TossPricesResponse.Item item = new TossPricesResponse.Item();

            item.setSymbol(product.getSourceProductCode());
            item.setLastPrice(FAKE_PRICE);
            item.setTimestamp(quotedAt);
            item.setCurrency("KRW");

            quotes.add(item);
        }

        TossInvestClient client = mock(TossInvestClient.class);

        when(client.fetchPrices(anyList())).thenReturn(quotes);

        return client;
    }

    private static AnnotationConfigApplicationContext context() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("priceRefreshJdbcTest", LocalDatabaseProperties.load())
        );
        context.register(RootConfig.class);
        context.refresh();

        return context;
    }
}
