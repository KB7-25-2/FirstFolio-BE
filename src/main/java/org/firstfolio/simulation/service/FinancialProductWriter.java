package org.firstfolio.simulation.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 등록의 DB 작업만 담당한다.
 *
 * <p>외부 API 호출과 트랜잭션을 분리하려고 서비스에서 떼어냈다. 수집 중에 커넥션을
 * 붙들고 있지 않도록, 가져오기가 끝난 뒤 여기서만 트랜잭션을 연다.</p>
 */
@Component
public class FinancialProductWriter {

    private static final Logger log = LogManager.getLogger(FinancialProductWriter.class);

    private final FinancialProductMapper financialProductMapper;

    public FinancialProductWriter(FinancialProductMapper financialProductMapper) {
        this.financialProductMapper = financialProductMapper;
    }

    /**
     * 아직 등록되지 않은 상품만 저장한다. 같은 원천 상품을 다시 가져와도 중복되지 않는다.
     *
     * <p>{@code (source_provider, source_product_code)}에 유니크 제약이 없어 조회 후 삽입으로
     * 막는다. 관리자가 수동으로 돌리는 작업이라 동시 실행은 상정하지 않는다.</p>
     */
    @Transactional
    public ProductImportResult registerNew(
            List<FinancialProduct> products,
            LocalDateTime referenceAt,
            LocalDateTime now
    ) {
        List<Long> insertedIds = new ArrayList<>();
        int skipped = 0;

        for (FinancialProduct product : products) {
            FinancialProduct existing = financialProductMapper.findBySource(
                    product.getSourceProvider(),
                    product.getSourceProductCode()
            );

            if (existing != null) {
                skipped++;
                continue;
            }

            product.setCreatedAt(now);
            product.setUpdatedAt(now);

            financialProductMapper.insert(product);
            insertedIds.add(product.getProductId());
        }

        log.info(
                "상품 등록 완료 등록={} 건너뜀={} referenceAt={}",
                insertedIds.size(),
                skipped,
                referenceAt
        );

        return new ProductImportResult(skipped, insertedIds, referenceAt);
    }
}
