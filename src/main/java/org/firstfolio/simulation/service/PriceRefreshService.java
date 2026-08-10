package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주식·펀드 기준 가격 갱신 (FUNC-040).
 *
 * <p>토스증권에서 현재가를 받아 {@code product_prices}에 새 행으로 쌓는다. 포트폴리오 평가는
 * 이렇게 저장된 값을 읽는다 — 평가할 때마다 외부를 부르면 Rate Limit에 걸리고 제공처 장애가
 * 그대로 사용자에게 전파된다 (v3 3.2절).</p>
 *
 * <h3>주식과 펀드를 한 번에 부른다</h3>
 *
 * <p>ETF도 토스에서 조회된다는 것을 실호출로 확인했다 (2026-08-06,
 * {@code DECISION_ETF_PRICE_SOURCE_20260806.md}). 공공데이터포털 ETF API는 <b>전일 종가</b>라
 * 하루 한 번만 값이 바뀌는데, 압축 예외 자산군은 실제 시세를 그대로 쓰기로 한 정책과 어긋났다.
 * 공공데이터는 상품 <b>등록</b>에서 계속 쓴다 — 종목명·기초지수는 그쪽에만 있다.</p>
 *
 * <h3>기준 시각</h3>
 *
 * <p>{@code reference_at}에는 <b>갱신 실행 시점</b>(요청받은 값)을 넣는다. 응답의 체결 시각이
 * 아니다. 유동성이 낮은 종목은 마지막 체결이 몇 분 전이라, 체결 시각을 그대로 쓰면 종목마다
 * 기준 시점이 흩어져 "같은 시점의 포트폴리오 평가"가 성립하지 않는다. 실제 체결 시각은
 * {@code generation_key}에 근거로 남긴다.</p>
 *
 * <h3>같은 체결값은 다시 쌓지 않는다</h3>
 *
 * <p>{@code generation_key}가 <b>체결 시각</b>을 담고 있고 유니크 제약이 걸려 있어,
 * 새 체결이 없으면 기준 시각을 바꿔 다시 불러도 저장되지 않는다(건너뜀으로 집계된다).
 * 장 마감 후 하루 종일 폴링해도 같은 가격이 수천 행 쌓이지 않는다는 뜻이다.</p>
 *
 * <p>그래서 <b>가격 행은 값이 실제로 바뀔 때만 늘어난다.</b> 평가가 읽는 "가장 최근 가격"의
 * {@code reference_at}이 조금 오래돼 보일 수 있는데, 그 시점 이후로 체결이 없었다는 뜻이라
 * 사실에 맞다.</p>
 *
 * <h3>부분 실패를 허용한다</h3>
 *
 * <p>메서드 전체를 트랜잭션으로 묶지 않는다. 한 종목이 실패해도 나머지는 저장돼야 하기 때문이다.
 * 각 저장은 독립적이고, 멱등성은 유니크 제약이 보장한다.</p>
 */
@Service
public class PriceRefreshService {

    private static final String SOURCE_TYPE_REAL = "REAL_DATA";
    private static final String KEY_PREFIX = "toss";

    private static final Logger log = LogManager.getLogger(PriceRefreshService.class);

    private final PriceQuoteFetcher quoteFetcher;
    private final ProductPriceMapper productPriceMapper;

    /**
     * 직전 가격 대비 허용 변동률. <b>정책 미확정 상태의 가정치다</b> (v3 7절).
     *
     * <p>초과해도 <b>저장은 한다.</b> 실제 시세를 서버가 거부하면 가격이 비어 평가·거래가 모두
     * 막히는데, 그게 값이 튄 것보다 위험하다. 대신 경고 로그를 남겨 추적할 수 있게 한다
     * (FUNC-040 "정책 범위를 벗어난 변동은 반영하지 않는다"의 완화 적용).</p>
     */
    private final BigDecimal maxChangeRate;

    public PriceRefreshService(
            PriceQuoteFetcher quoteFetcher,
            ProductPriceMapper productPriceMapper,
            @Value("${price.max-change-rate:0.30}") BigDecimal maxChangeRate
    ) {
        this.quoteFetcher = quoteFetcher;
        this.productPriceMapper = productPriceMapper;
        this.maxChangeRate = maxChangeRate;
    }

    /**
     * 대상 상품의 기준 가격을 새 시점으로 저장한다.
     *
     * @param referenceAt 가격 기준 시점(UTC). 미래 시각은 거부한다
     * @param productIds  null이거나 비어 있으면 공개된 주식·펀드 전체
     */
    public PriceRefreshResult refresh(LocalDateTime referenceAt, List<Long> productIds) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        requireValidReferenceAt(referenceAt, now);

        List<FinancialProduct> targets = quoteFetcher.findTargets(productIds);

        if (targets.isEmpty()) {
            log.info("가격 갱신 대상이 없습니다 referenceAt={}", referenceAt);

            return new PriceRefreshResult(referenceAt, 0, 0, 0);
        }

        Map<String, FinancialProduct> bySymbol = quoteFetcher.indexBySymbol(targets);
        Map<Long, BigDecimal> previousPrices = previousPrices(targets);
        Map<String, TossPricesResponse.Item> quotes = quoteFetcher.fetchQuotes(bySymbol.keySet());

        int created = 0;

        for (Map.Entry<String, FinancialProduct> entry : bySymbol.entrySet()) {
            TossPricesResponse.Item quote = quotes.get(entry.getKey());

            if (save(entry.getValue(), entry.getKey(), quote, referenceAt, now, previousPrices)) {
                created++;
            }
        }

        int processed = targets.size();
        int skipped = processed - created;

        log.info(
                "가격 갱신 완료 referenceAt={} 대상={} 저장={} 건너뜀={}",
                referenceAt,
                processed,
                created,
                skipped
        );

        return new PriceRefreshResult(referenceAt, processed, created, skipped);
    }

    /**
     * 한 종목의 가격을 저장한다.
     *
     * @return 실제로 저장했으면 true. 값이 없거나 이미 있으면 false
     */
    private boolean save(
            FinancialProduct product,
            String symbol,
            TossPricesResponse.Item quote,
            LocalDateTime referenceAt,
            LocalDateTime now,
            Map<Long, BigDecimal> previousPrices
    ) {
        // 토스는 모르는 종목코드를 오류 없이 결과에서 뺀다. 조용히 넘기면 원인을 못 찾는다.
        if (quote == null) {
            log.warn(
                    "시세 응답에 종목이 없습니다 productId={} symbol={} (등록된 종목코드인지 확인 필요)",
                    product.getProductId(),
                    symbol
            );

            return false;
        }

        // 없는 가격을 만들어 내지 않는다 (FUNC-036/040).
        BigDecimal price = quoteFetcher.lastPrice(quote);

        if (price == null) {
            log.warn(
                    "가격이 올바르지 않습니다 productId={} price={}",
                    product.getProductId(),
                    quote.getLastPrice()
            );

            return false;
        }

        warnIfPriceJumped(product, previousPrices.get(product.getProductId()), price);

        ProductPrice row = new ProductPrice();

        row.setProductId(product.getProductId());
        row.setPrice(price);
        row.setReferenceAt(referenceAt);
        row.setSourceType(SOURCE_TYPE_REAL);
        row.setGenerationKey(generationKey(symbol, quote.getTimestamp(), referenceAt));
        row.setCreatedAt(now);

        try {
            productPriceMapper.insert(row);

            return true;
        } catch (DuplicateKeyException exception) {
            // 같은 상품·기준 시각 또는 같은 생성 키가 이미 있다. 배치 재실행에서 정상이다.
            log.debug("이미 저장된 가격입니다 productId={} key={}", product.getProductId(), row.getGenerationKey());

            return false;
        }
    }

    /**
     * 생성 키 겸 계산 근거. {@code toss:005930:2026-08-06T04:19:15Z}
     *
     * <p>{@code product_prices}에 계산 근거를 담을 컬럼이 없어 이 키가 그 역할을 겸한다.
     * 어느 제공처의 <b>언제 체결된</b> 값인지가 키에 남는다.</p>
     *
     * <p>체결 시각을 못 읽으면 기준 시각으로 대신한다. 키가 없으면 저장 자체를 못 하는데,
     * 그것 때문에 가격을 버리는 것은 과하다.</p>
     */
    static String generationKey(String symbol, String quotedAt, LocalDateTime referenceAt) {
        String stamp;

        try {
            stamp = OffsetDateTime.parse(quotedAt)
                    .atZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception exception) {
            stamp = referenceAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        return KEY_PREFIX + ":" + symbol + ":" + stamp + "Z";
    }

    private void warnIfPriceJumped(FinancialProduct product, BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() <= 0) {
            return;
        }

        BigDecimal change = current.subtract(previous).abs()
                .divide(previous, 4, RoundingMode.HALF_UP);

        if (change.compareTo(maxChangeRate) > 0) {
            log.warn(
                    "직전 가격 대비 변동이 큽니다 productId={} 이전={} 현재={} 변동률={} (가정치 {} 초과, 저장은 계속)",
                    product.getProductId(),
                    previous,
                    current,
                    change,
                    maxChangeRate
            );
        }
    }

    private Map<Long, BigDecimal> previousPrices(List<FinancialProduct> targets) {
        List<Long> productIds = new ArrayList<>();

        for (FinancialProduct product : targets) {
            productIds.add(product.getProductId());
        }

        Map<Long, BigDecimal> previous = new HashMap<>();

        for (ProductPrice price : productPriceMapper.findLatestByProductIds(productIds)) {
            previous.put(price.getProductId(), price.getPrice());
        }

        return previous;
    }

    /** 미래 시각으로는 가격을 만들지 않는다 (FUNC-040 예외/제한사항). */
    private static void requireValidReferenceAt(LocalDateTime referenceAt, LocalDateTime now) {
        if (referenceAt == null) {
            throw new ApiException(ErrorCode.PRICE_POLICY_INVALID, "가격 기준 시점이 필요합니다.");
        }

        if (referenceAt.isAfter(now)) {
            throw new ApiException(ErrorCode.PRICE_POLICY_INVALID, "미래 시점의 가격은 만들 수 없습니다.");
        }
    }
}
