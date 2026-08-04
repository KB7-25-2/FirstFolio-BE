package org.firstfolio.simulation.service.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.simulation.client.datago.EtfPriceClient;
import org.firstfolio.simulation.client.datago.EtfPriceResponse;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.EtfCatalog;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.FundRealTerms;
import org.firstfolio.simulation.domain.UncompressedSimulationTerms;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 공공데이터포털 ETF 시세로 펀드 5종목을 등록한다 (SIMULATION_POLICY_v3 1절, 6절).
 *
 * <p>v3 6절이 펀드를 ETF로 대체하기로 확정했다. ETF는 <b>만기가 없고 실시간 시세로
 * 가격이 결정</b>되어 주식과 성격이 같으므로 시간 압축을 적용하지 않는다.</p>
 *
 * <p>시세 조회는 종목코드가 실제로 거래되는지 확인하는 용도다. 유형(주식형·채권형·혼합형)은
 * API가 주지 않아 {@link EtfCatalog} 내부 매핑에서 가져온다.</p>
 */
@Component
public class EtfCollector implements ProductCollector {

    public static final String SOURCE_PROVIDER = "DATA_GO_KR_ETF";

    private static final Logger log = LogManager.getLogger(EtfCollector.class);

    private final EtfPriceClient etfPriceClient;
    private final TermsJsonCodec termsJsonCodec;

    public EtfCollector(EtfPriceClient etfPriceClient, TermsJsonCodec termsJsonCodec) {
        this.etfPriceClient = etfPriceClient;
        this.termsJsonCodec = termsJsonCodec;
    }

    @Override
    public String sourceProvider() {
        return SOURCE_PROVIDER;
    }

    @Override
    public List<FinancialProduct> collect(LocalDateTime referenceAt, LocalDateTime now) {
        List<EtfPriceResponse.Item> quotes = etfPriceClient.fetchLatest(
                EtfCatalog.allShortCodes(),
                referenceAt.toLocalDate()
        );

        Set<String> tradable = new HashSet<>();

        for (EtfPriceResponse.Item quote : quotes) {
            if (quote.getSrtnCd() != null && quote.getClpr() != null) {
                tradable.add(quote.getSrtnCd());
            }
        }

        List<FinancialProduct> products = new ArrayList<>();

        for (EtfCatalog etf : EtfCatalog.all()) {
            if (!tradable.contains(etf.getShortCode())) {
                // 시세가 없는 종목은 등록하지 않는다. 잘못된 코드가 조용히 들어가면
                // 나중에 가격 갱신·거래에서 값이 비어 원인을 찾기 어렵다.
                log.warn("시세를 확인하지 못해 등록에서 제외합니다 srtnCd={}", etf.getShortCode());
                continue;
            }

            products.add(toFinancialProduct(etf, referenceAt, now));
        }

        log.info("ETF 변환 완료 대상={} 시세확인={} 변환={}",
                EtfCatalog.all().size(), tradable.size(), products.size());

        return products;
    }

    private FinancialProduct toFinancialProduct(
            EtfCatalog etf,
            LocalDateTime referenceAt,
            LocalDateTime now
    ) {
        FundRealTerms realTerms = new FundRealTerms();

        realTerms.setFundType(etf.getFundType().name());

        UncompressedSimulationTerms simulationTerms =
                new UncompressedSimulationTerms(UncompressedSimulationTerms.REASON_ETF);

        simulationTerms.setRegisteredAt(now);

        FinancialProduct product = new FinancialProduct();

        product.setAssetType(AssetType.FUND);
        // 가명은 관리자가 검토 후 입력한다. 공개 전환 시 이름이 그대로면 거부된다.
        product.setDisplayName(etf.getRealName());
        product.setDescription(null);
        product.setSourceProvider(SOURCE_PROVIDER);
        product.setSourceProductCode(etf.getShortCode());
        product.setSourceProductName(etf.getRealName());
        product.setSourceReferenceAt(referenceAt);
        product.setRealTermsJson(termsJsonCodec.write(realTerms));
        product.setSimulationTermsJson(termsJsonCodec.write(simulationTerms));
        product.setRiskLevel(riskLevel(etf.getFundType()));
        product.setActive(false);

        return product;
    }

    /**
     * 무엇에 투자하느냐로 위험도를 나눈다. 채권형은 국채·우량 회사채에 투자하므로
     * 투자적격 회사채와 같은 {@code MEDIUM}, 주식형은 주식과 같은 {@code HIGH},
     * 혼합형은 그 사이라 {@code MEDIUM}으로 둔다.
     */
    private static String riskLevel(EtfCatalog.FundType fundType) {
        return fundType == EtfCatalog.FundType.EQUITY ? "HIGH" : "MEDIUM";
    }
}
