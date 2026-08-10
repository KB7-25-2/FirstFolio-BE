package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 토스증권에서 주식·펀드 시세를 받아 오는 자리.
 *
 * <p>같은 조회를 두 곳이 쓴다 — 장중 캐시 갱신({@link PriceCacheScheduler})과
 * 종가 저장({@link PriceRefreshService})이다. 대상 선정·종목코드 매핑·분할 호출을 각자
 * 들고 있으면 <b>한쪽만 고쳐지는 날이 온다.</b></p>
 *
 * <h3>주식과 펀드를 한 번에 부른다</h3>
 *
 * <p>ETF도 토스에서 조회된다는 것을 실호출로 확인했다 (2026-08-06,
 * {@code DECISION_ETF_PRICE_SOURCE_20260806.md}). 공공데이터포털 ETF API는 <b>전일 종가</b>라
 * 하루 한 번만 값이 바뀌는데, 압축 예외 자산군은 실제 시세를 그대로 쓰기로 한 정책과 어긋났다.
 * 공공데이터는 상품 <b>등록</b>에서 계속 쓴다 — 종목명·기초지수는 그쪽에만 있다.</p>
 */
@Component
public class PriceQuoteFetcher {

    /** 기준 가격으로 평가하는 자산군. 예·적금·채권은 원금으로 평가하므로 가격이 없다. */
    private static final List<AssetType> PRICE_BASED = List.of(AssetType.STOCK, AssetType.FUND);

    /** 토스 한 번 호출의 종목 수 상한. 클라이언트도 같은 값으로 막는다. */
    private static final int MAX_SYMBOLS_PER_CALL = 200;

    /** 가격 자릿수. {@code DECIMAL(19, 4)} 컬럼과 맞춘다. */
    private static final int PRICE_SCALE = 4;

    private static final Logger log = LogManager.getLogger(PriceQuoteFetcher.class);

    private final FinancialProductMapper financialProductMapper;
    private final TossInvestClient tossInvestClient;

    public PriceQuoteFetcher(
            FinancialProductMapper financialProductMapper,
            TossInvestClient tossInvestClient
    ) {
        this.financialProductMapper = financialProductMapper;
        this.tossInvestClient = tossInvestClient;
    }

    /**
     * 가격을 채울 대상 상품.
     *
     * @param productIds null이거나 비어 있으면 공개된 주식·펀드 전체
     */
    public List<FinancialProduct> findTargets(List<Long> productIds) {
        return financialProductMapper.findPriceTargets(PRICE_BASED, emptyToNull(productIds));
    }

    /**
     * 종목코드 → 상품. <b>코드가 없는 상품은 빠진다</b> — 조회할 방법이 없다.
     *
     * <p>빠진 만큼 {@link #findTargets} 결과보다 작아진다. 호출한 쪽이 "대상 수"를 셀 때는
     * 이 맵이 아니라 원래 목록을 봐야 한다.</p>
     */
    public Map<String, FinancialProduct> indexBySymbol(List<FinancialProduct> targets) {
        Map<String, FinancialProduct> bySymbol = new LinkedHashMap<>();

        if (targets == null) {
            return bySymbol;
        }

        for (FinancialProduct product : targets) {
            String symbol = product.getSourceProductCode();

            if (symbol == null || symbol.isBlank()) {
                log.warn("종목코드가 없어 가격을 갱신할 수 없습니다 productId={}", product.getProductId());
                continue;
            }

            bySymbol.put(symbol.trim(), product);
        }

        return bySymbol;
    }

    /** 상한을 넘으면 나눠 부른다. 종목이 늘어도 호출부가 바뀌지 않게 한다. */
    public Map<String, TossPricesResponse.Item> fetchQuotes(Collection<String> symbols) {
        Map<String, TossPricesResponse.Item> quotes = new HashMap<>();

        if (symbols == null || symbols.isEmpty()) {
            return quotes;
        }

        List<String> all = new ArrayList<>(symbols);

        for (int from = 0; from < all.size(); from += MAX_SYMBOLS_PER_CALL) {
            List<String> chunk = all.subList(from, Math.min(from + MAX_SYMBOLS_PER_CALL, all.size()));

            for (TossPricesResponse.Item item : tossInvestClient.fetchPrices(chunk)) {
                if (item.getSymbol() != null) {
                    quotes.put(item.getSymbol().trim(), item);
                }
            }
        }

        return quotes;
    }

    /**
     * 응답의 체결가를 저장·평가에 쓸 자릿수로 맞춘다. <b>쓸 수 없는 값이면 null이다.</b>
     *
     * <p>없는 가격을 만들어 내지 않는다 (FUNC-036/040). 캐시와 DB가 <b>같은 자릿수</b>를
     * 써야 재시작 전후로 평가액이 미세하게 달라지지 않는다.</p>
     */
    public BigDecimal lastPrice(TossPricesResponse.Item quote) {
        if (quote == null || quote.getLastPrice() == null || quote.getLastPrice().signum() <= 0) {
            return null;
        }

        return quote.getLastPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 빈 목록은 "전체"와 같은 뜻이므로 null로 바꾼다.
     *
     * <p>매퍼에 빈 목록이 그대로 가면 {@code IN ()}이 만들어져 SQL 오류가 난다.</p>
     */
    private static List<Long> emptyToNull(List<Long> productIds) {
        return productIds == null || productIds.isEmpty() ? null : productIds;
    }
}
