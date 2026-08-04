package org.firstfolio.simulation.service.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.simulation.client.datago.BondBasicInfo;
import org.firstfolio.simulation.client.datago.BondIssueInfoClient;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.BondRealTerms;
import org.firstfolio.simulation.domain.CreditRating;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.firstfolio.simulation.service.TermsJsonCodec;
import org.firstfolio.simulation.service.TimeCompressionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 채권기본정보에서 국고채·회사채를 수집한다 (SIMULATION_POLICY_v3 6절).
 *
 * <p>대상 종목은 설정값으로 지정한다. 채권은 종목이 29,000건이 넘어 전량 수집이 무의미하고,
 * 국고채 2·회사채 3이라는 구성이 정해져 있기 때문이다 (v3 1절).</p>
 */
@Component
public class BondCollector implements ProductCollector {

    public static final String SOURCE_PROVIDER = "DATA_GO_KR_BOND";

    private static final Logger log = LogManager.getLogger(BondCollector.class);

    private final BondIssueInfoClient bondIssueInfoClient;
    private final TimeCompressionPolicy timeCompressionPolicy;
    private final TermsJsonCodec termsJsonCodec;
    private final List<String> isinCodes;

    public BondCollector(
            BondIssueInfoClient bondIssueInfoClient,
            TimeCompressionPolicy timeCompressionPolicy,
            TermsJsonCodec termsJsonCodec,
            @Value("${datago.bond.isin-codes:}") String isinCodes
    ) {
        this.bondIssueInfoClient = bondIssueInfoClient;
        this.timeCompressionPolicy = timeCompressionPolicy;
        this.termsJsonCodec = termsJsonCodec;
        this.isinCodes = parseCodes(isinCodes);
    }

    private static List<String> parseCodes(String raw) {
        List<String> codes = new ArrayList<>();

        if (raw == null || raw.isBlank()) {
            return codes;
        }

        for (String code : raw.split(",")) {
            String trimmed = code.trim();

            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }

        return codes;
    }

    @Override
    public String sourceProvider() {
        return SOURCE_PROVIDER;
    }

    @Override
    public List<FinancialProduct> collect(LocalDateTime referenceAt, LocalDateTime now) {
        if (isinCodes.isEmpty()) {
            throw new ApiException(
                    org.firstfolio.exception.ErrorCode.INVALID_SOURCE_PRODUCT,
                    "수집할 채권 종목이 설정되지 않았습니다 (datago.bond.isin-codes)."
            );
        }

        LocalDate baseDate = referenceAt.toLocalDate();
        List<BondBasicInfo> collected = bondIssueInfoClient.fetchLatest(isinCodes, baseDate);

        List<FinancialProduct> products = new ArrayList<>();

        for (BondBasicInfo bond : collected) {
            try {
                products.add(toFinancialProduct(bond, referenceAt, now));
            } catch (ApiException exception) {
                // 만기가 지났거나 조건이 불완전한 종목은 임의로 만들지 않고 제외한다.
                log.warn(
                        "채권 변환 제외 isinCd={} 사유={}",
                        bond.getIsinCd(),
                        exception.getMessage()
                );
            }
        }

        log.info("채권 변환 완료 수집={} 변환={}", collected.size(), products.size());

        return products;
    }

    private FinancialProduct toFinancialProduct(
            BondBasicInfo bond,
            LocalDateTime referenceAt,
            LocalDateTime now
    ) {
        AssetType assetType = AssetType.BOND;

        // 사용자가 오늘 사서 만기까지 들고 있는 기간이 실제 경험 시간이다.
        int remainingMonths = (int) bond.remainingMonths(
                now.toLocalDate()
        );

        SimulationTerms simulationTerms = timeCompressionPolicy.compress(
                assetType,
                remainingMonths,
                bond.getInterestIntervalMonths(),
                now
        );

        FinancialProduct product = new FinancialProduct();

        product.setAssetType(assetType);
        // 가명은 관리자가 검토 후 입력한다. 공개 전환 시 이름이 그대로면 거부된다.
        product.setDisplayName(bond.getIsinCdNm());
        product.setDescription(null);
        product.setSourceProvider(SOURCE_PROVIDER);
        // ISIN은 국제 표준 코드라 그 자체로 유일하다.
        product.setSourceProductCode(bond.getIsinCd());
        product.setSourceProductName(bond.getIsinCdNm());
        product.setSourceReferenceAt(referenceAt);
        product.setRealTermsJson(termsJsonCodec.write(toRealTerms(bond, remainingMonths)));
        product.setSimulationTermsJson(termsJsonCodec.write(simulationTerms));
        // 국채는 등급이 없어 LOW, 회사채는 투자적격/투기등급으로 갈린다.
        product.setRiskLevel(CreditRating.riskLevel(bond.getCreditRating()));
        product.setActive(false);

        return product;
    }

    private BondRealTerms toRealTerms(BondBasicInfo bond, int remainingMonths) {
        BondRealTerms terms = new BondRealTerms();

        terms.setCouponRate(bond.getCouponRate());
        terms.setMaturityMonths(remainingMonths);
        terms.setInterestIntervalMonths(bond.getInterestIntervalMonths());
        terms.setInterestType(bond.getInterestType());
        terms.setBondCategory(bond.getBondCategory());
        terms.setCreditRating(bond.getCreditRating());

        return terms;
    }

    /** 테스트에서 기준 시각을 고정하기 위해 노출한다. */
    static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
