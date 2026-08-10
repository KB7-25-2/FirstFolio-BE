package org.firstfolio.simulation.service.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.simulation.client.finlife.FinlifeClient;
import org.firstfolio.simulation.client.finlife.FinlifeProduct;
import org.firstfolio.simulation.client.finlife.FinlifeProductType;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.RealTerms;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.firstfolio.simulation.service.TimeCompressionPolicy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 금융감독원 finlife에서 예·적금을 수집한다 (SIMULATION_POLICY_v3 6절).
 */
@Component
public class DepositSavingCollector implements ProductCollector {

    public static final String SOURCE_PROVIDER = "FSS_FINLIFE";

    private static final Logger log = LogManager.getLogger(DepositSavingCollector.class);

    /** 예·적금은 원금이 보장되므로 위험도를 LOW로 둔다. finlife는 위험도를 제공하지 않는다. */
    private static final String RISK_LEVEL = "LOW";

    /** 정기예금·정기적금은 만기일시지급. finlife가 주지 않는 값이라 가정치로 표시한다. */
    private static final String INTEREST_INTERVAL_MATURITY = "MATURITY";
    private static final String SOURCE_ASSUMED = "ASSUMED";

    private final FinlifeClient finlifeClient;
    private final TimeCompressionPolicy timeCompressionPolicy;
    private final TermsJsonCodec termsJsonCodec;

    public DepositSavingCollector(
            FinlifeClient finlifeClient,
            TimeCompressionPolicy timeCompressionPolicy,
            TermsJsonCodec termsJsonCodec
    ) {
        this.finlifeClient = finlifeClient;
        this.timeCompressionPolicy = timeCompressionPolicy;
        this.termsJsonCodec = termsJsonCodec;
    }

    @Override
    public String sourceProvider() {
        return SOURCE_PROVIDER;
    }

    @Override
    public List<FinancialProduct> collect(LocalDateTime referenceAt, LocalDateTime now) {
        List<FinlifeProduct> collected = new ArrayList<>();

        collected.addAll(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT));
        collected.addAll(finlifeClient.fetchAll(FinlifeProductType.SAVING));

        List<FinancialProduct> products = new ArrayList<>();

        for (FinlifeProduct source : collected) {
            try {
                products.add(toFinancialProduct(source, referenceAt, now));
            } catch (ApiException exception) {
                // 만기를 알 수 없는 항목은 임의로 만들지 않고 제외한다 (FUNC-038/039).
                log.warn(
                        "상품 변환 제외 code={} 사유={}",
                        source.sourceProductCode(),
                        exception.getMessage()
                );
            }
        }

        log.info("예·적금 변환 완료 수집={} 변환={}", collected.size(), products.size());

        return products;
    }

    private FinancialProduct toFinancialProduct(
            FinlifeProduct source,
            LocalDateTime referenceAt,
            LocalDateTime now
    ) {
        AssetType assetType = AssetType.DEPOSIT_SAVINGS;

        SimulationTerms simulationTerms = timeCompressionPolicy.compress(
                assetType,
                source.getSaveTrmMonths(),
                now
        );

        FinancialProduct product = new FinancialProduct();

        product.setAssetType(assetType);
        // 가명은 관리자가 검토 후 입력한다. 공개 전환 시 이름이 그대로면 거부된다.
        product.setDisplayName(source.sourceProductName());
        product.setDescription(null);
        product.setSourceProvider(SOURCE_PROVIDER);
        product.setSourceProductCode(source.sourceProductCode());
        product.setSourceProductName(source.sourceProductName());
        product.setSourceReferenceAt(referenceAt);
        product.setRealTermsJson(termsJsonCodec.write(toRealTerms(source)));
        product.setSimulationTermsJson(termsJsonCodec.write(simulationTerms));
        product.setRiskLevel(RISK_LEVEL);
        product.setActive(false);

        return product;
    }

    private RealTerms toRealTerms(FinlifeProduct source) {
        RealTerms terms = new RealTerms();

        terms.setInterestRate(source.getBaseInterestRate());
        terms.setMaturityMonths(source.getSaveTrmMonths());
        terms.setInterestInterval(INTEREST_INTERVAL_MATURITY);
        terms.setInterestIntervalSource(SOURCE_ASSUMED);
        terms.setInterestRateType(normalizeRateType(source.getInterestRateType()));
        terms.setReserveType(normalizeReserveType(source.getReserveType()));

        return terms;
    }

    private static String normalizeRateType(String rateTypeName) {
        if (rateTypeName == null) {
            return null;
        }

        return rateTypeName.contains("복리") ? "COMPOUND" : "SIMPLE";
    }

    private static String normalizeReserveType(String reserveTypeName) {
        if (reserveTypeName == null) {
            return null;
        }

        return reserveTypeName.contains("자유") ? "FLEXIBLE" : "FIXED";
    }
}
