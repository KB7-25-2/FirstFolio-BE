package org.firstfolio.simulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.client.finlife.FinlifeClient;
import org.firstfolio.simulation.client.finlife.FinlifeProduct;
import org.firstfolio.simulation.client.finlife.FinlifeProductType;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.RealTerms;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제 금융상품 데이터를 가명 모의 상품으로 변환·등록한다 (FUNC-038).
 *
 * <p>흐름: 외부 API 수집 → 실제 조건 정규화 → 압축 계산 → <b>비공개 상태로</b> 등록.
 * 관리자가 목록에서 검토하고 가명({@code display_name})을 입력한 뒤 공개로 전환한다.</p>
 *
 * <p>등록 시점의 {@code display_name}에는 실제 상품명이 들어간다. 비공개 상태라
 * 사용자에게 노출되지 않으며, 이름을 바꾸지 않으면 공개 전환이 거부된다.</p>
 */
@Service
public class FinancialProductImportService {

    private static final Logger log = LogManager.getLogger(FinancialProductImportService.class);

    /** 원천 데이터 제공처 식별자. 어느 외부 API를 호출할지 결정한다. */
    public static final String SOURCE_FSS_FINLIFE = "FSS_FINLIFE";

    /** 예·적금은 원금이 보장되므로 위험도를 LOW로 둔다. finlife는 위험도를 제공하지 않는다. */
    private static final String DEPOSIT_SAVINGS_RISK_LEVEL = "LOW";

    /** 정기예금·정기적금은 만기일시지급. finlife가 주지 않는 값이라 가정치로 표시한다. */
    private static final String INTEREST_INTERVAL_MATURITY = "MATURITY";
    private static final String SOURCE_ASSUMED = "ASSUMED";

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    private final FinlifeClient finlifeClient;
    private final TimeCompressionPolicy timeCompressionPolicy;
    private final FinancialProductWriter financialProductWriter;

    public FinancialProductImportService(
            FinlifeClient finlifeClient,
            TimeCompressionPolicy timeCompressionPolicy,
            FinancialProductWriter financialProductWriter
    ) {
        this.finlifeClient = finlifeClient;
        this.timeCompressionPolicy = timeCompressionPolicy;
        this.financialProductWriter = financialProductWriter;
    }

    public ProductImportResult importProducts(String sourceProvider, LocalDateTime referenceAt) {
        if (!SOURCE_FSS_FINLIFE.equals(sourceProvider)) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "지원하지 않는 원천 데이터 제공처입니다: " + sourceProvider
            );
        }

        // 외부 호출은 트랜잭션 밖에서 먼저 끝낸다.
        List<FinlifeProduct> collected = new ArrayList<>();

        collected.addAll(finlifeClient.fetchAll(FinlifeProductType.DEPOSIT));
        collected.addAll(finlifeClient.fetchAll(FinlifeProductType.SAVING));

        List<FinancialProduct> products = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

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

        log.info("상품 변환 완료 수집={} 변환={}", collected.size(), products.size());

        return financialProductWriter.registerNew(products, referenceAt, now);
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
        product.setSourceProvider(SOURCE_FSS_FINLIFE);
        product.setSourceProductCode(source.sourceProductCode());
        product.setSourceProductName(source.sourceProductName());
        product.setSourceReferenceAt(referenceAt);
        product.setRealTermsJson(writeJson(toRealTerms(source)));
        product.setSimulationTermsJson(writeJson(simulationTerms));
        product.setRiskLevel(DEPOSIT_SAVINGS_RISK_LEVEL);
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "상품 조건을 저장할 수 없습니다.",
                    exception
            );
        }
    }
}
