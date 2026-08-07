package org.firstfolio.simulation.service.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.simulation.client.toss.TossInvestClient;
import org.firstfolio.simulation.client.toss.TossPricesResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.KrxStock;
import org.firstfolio.simulation.domain.StockRealTerms;
import org.firstfolio.simulation.domain.UncompressedSimulationTerms;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 토스증권 Open API로 주식 10종목을 등록한다 (SIMULATION_POLICY_v3 2.2절).
 *
 * <p>다른 자산군과 달리 <b>시세 API가 종목명을 주지 않아</b> 종목 정보는 {@link KrxStock}
 * 내부 매핑에서 가져온다. 시세 조회는 <b>종목코드가 실제로 거래되는지 확인</b>하는 용도다 —
 * 오탈자가 있으면 그 종목은 등록되지 않는다.</p>
 *
 * <p>주식은 시간 압축 예외라 {@code TimeCompressionPolicy}를 쓰지 않는다.</p>
 */
@Component
public class StockCollector implements ProductCollector {

    public static final String SOURCE_PROVIDER = "TOSSINVEST";

    private static final Logger log = LogManager.getLogger(StockCollector.class);

    /** 주식은 원금이 보장되지 않고 가격 변동이 크다. */
    private static final String RISK_LEVEL = "HIGH";

    private final TossInvestClient tossInvestClient;
    private final TermsJsonCodec termsJsonCodec;

    public StockCollector(TossInvestClient tossInvestClient, TermsJsonCodec termsJsonCodec) {
        this.tossInvestClient = tossInvestClient;
        this.termsJsonCodec = termsJsonCodec;
    }

    @Override
    public String sourceProvider() {
        return SOURCE_PROVIDER;
    }

    @Override
    public List<FinancialProduct> collect(LocalDateTime referenceAt, LocalDateTime now) {
        List<TossPricesResponse.Item> quotes =
                tossInvestClient.fetchPrices(KrxStock.allSymbols());

        Set<String> tradable = new HashSet<>();

        for (TossPricesResponse.Item quote : quotes) {
            if (quote.getSymbol() != null && quote.getLastPrice() != null) {
                tradable.add(quote.getSymbol());
            }
        }

        List<FinancialProduct> products = new ArrayList<>();

        for (KrxStock stock : KrxStock.all()) {
            if (!tradable.contains(stock.getSymbol())) {
                // 시세가 없는 종목코드는 등록하지 않는다. 잘못된 코드가 조용히 들어가면
                // 나중에 가격 갱신·거래에서 값이 비어 원인을 찾기 어렵다.
                log.warn(
                        "시세를 확인하지 못해 등록에서 제외합니다 symbol={}",
                        stock.getSymbol()
                );
                continue;
            }

            products.add(toFinancialProduct(stock, referenceAt, now));
        }

        log.info("주식 변환 완료 대상={} 시세확인={} 변환={}",
                KrxStock.all().size(), tradable.size(), products.size());

        return products;
    }

    private FinancialProduct toFinancialProduct(
            KrxStock stock,
            LocalDateTime referenceAt,
            LocalDateTime now
    ) {
        StockRealTerms realTerms = new StockRealTerms();

        realTerms.setMarket(stock.getMarket());
        realTerms.setSector(stock.getSector());

        UncompressedSimulationTerms simulationTerms =
                new UncompressedSimulationTerms(UncompressedSimulationTerms.REASON_STOCK);

        simulationTerms.setRegisteredAt(now);

        FinancialProduct product = new FinancialProduct();

        product.setAssetType(AssetType.STOCK);
        // 가명은 관리자가 검토 후 입력한다. 공개 전환 시 이름이 그대로면 거부된다.
        product.setDisplayName(stock.getRealName());
        product.setDescription(null);
        product.setSourceProvider(SOURCE_PROVIDER);
        // KRX 종목코드는 그 자체로 유일하다.
        product.setSourceProductCode(stock.getSymbol());
        product.setSourceProductName(stock.getRealName());
        product.setSourceReferenceAt(referenceAt);
        product.setRealTermsJson(termsJsonCodec.write(realTerms));
        product.setSimulationTermsJson(termsJsonCodec.write(simulationTerms));
        product.setRiskLevel(RISK_LEVEL);
        product.setActive(false);

        return product;
    }
}
