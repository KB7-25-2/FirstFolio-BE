package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.service.collector.ProductCollector;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 금융상품 데이터를 가명 모의 상품으로 변환·등록한다 (FUNC-038).
 *
 * <p>흐름: 외부 API 수집 → 실제 조건 정규화 → 압축 계산 → <b>비공개 상태로</b> 등록.
 * 관리자가 목록에서 검토하고 가명({@code display_name})을 입력한 뒤 공개로 전환한다.</p>
 *
 * <p>자산군별 수집·변환은 {@link ProductCollector} 구현체가 맡는다. 이 클래스는
 * {@code source_provider}로 수집기를 고르고 저장을 위임하기만 한다.</p>
 */
@Service
public class FinancialProductImportService {

    private static final Logger log = LogManager.getLogger(FinancialProductImportService.class);

    private final Map<String, ProductCollector> collectorsByProvider = new LinkedHashMap<>();
    private final FinancialProductWriter financialProductWriter;

    public FinancialProductImportService(
            List<ProductCollector> collectors,
            FinancialProductWriter financialProductWriter
    ) {
        for (ProductCollector collector : collectors) {
            collectorsByProvider.put(collector.sourceProvider(), collector);
        }

        this.financialProductWriter = financialProductWriter;
    }

    public ProductImportResult importProducts(String sourceProvider, LocalDateTime referenceAt) {
        ProductCollector collector = collectorsByProvider.get(sourceProvider);

        if (collector == null) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "지원하지 않는 원천 데이터 제공처입니다: " + sourceProvider
                            + " (사용 가능: " + String.join(", ", collectorsByProvider.keySet()) + ")"
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // 외부 호출은 트랜잭션 밖에서 먼저 끝낸다.
        List<FinancialProduct> products = collector.collect(referenceAt, now);

        log.info("상품 수집 완료 provider={} 건수={}", sourceProvider, products.size());

        return financialProductWriter.registerNew(products, referenceAt, now);
    }
}
