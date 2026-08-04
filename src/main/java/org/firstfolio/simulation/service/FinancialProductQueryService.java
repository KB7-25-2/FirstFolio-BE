package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.domain.SourceProviderLabel;
import org.firstfolio.simulation.dto.response.ProductDetailResponse;
import org.firstfolio.simulation.dto.response.ProductPageResponse;
import org.firstfolio.simulation.dto.response.ProductSummaryResponse;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자용 모의 상품 조회 (FUNC-031/032/039).
 *
 * <p>학습 완료 여부로 상품을 걸러내지 않는다. 모든 공개 상품은 처음부터 선택할 수 있다
 * (v3 4절, PROJECT_SPEC 6.1). 이 서비스가 사용자 커리큘럼을 아예 참조하지 않는 것이
 * 그 규칙을 지키는 방법이다.</p>
 */
@Service
public class FinancialProductQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final FinancialProductMapper financialProductMapper;
    private final ProductPriceMapper productPriceMapper;
    private final TermsJsonCodec termsJsonCodec;

    public FinancialProductQueryService(
            FinancialProductMapper financialProductMapper,
            ProductPriceMapper productPriceMapper,
            TermsJsonCodec termsJsonCodec
    ) {
        this.financialProductMapper = financialProductMapper;
        this.productPriceMapper = productPriceMapper;
        this.termsJsonCodec = termsJsonCodec;
    }

    /**
     * 공개 상품의 상세를 조회한다 (FUNC-032).
     *
     * <p>비공개 상품은 존재하더라도 찾을 수 없는 것으로 처리한다. "있지만 볼 수 없다"와
     * "없다"를 구분해 주면 검수 대기 중인 상품의 존재가 드러난다.</p>
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse findById(Long productId) {
        FinancialProduct product = financialProductMapper.findActiveById(productId);

        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ProductDetailResponse response = new ProductDetailResponse();

        response.setProductId(product.getProductId());
        response.setDisplayName(product.getDisplayName());
        response.setAssetType(product.getAssetType().name());
        response.setDescription(product.getDescription());
        response.setRiskLevel(product.getRiskLevel());

        // 압축 대상만 기간을 병기한다. 주식·ETF는 만기가 없어 생략한다.
        if (product.getAssetType().isTimeCompressed()) {
            response.setSimulationTerms(termsJsonCodec.read(product.getSimulationTermsJson()));
            response.setRealTerms(termsJsonCodec.read(product.getRealTermsJson()));
        }

        // 저장된 기준 가격이 있을 때만 채운다. 없으면 임의 값을 만들지 않고 생략한다.
        ProductPrice latestPrice = productPriceMapper.findLatestByProductId(productId);

        if (latestPrice != null) {
            response.setCurrentPrice(latestPrice.getPrice());
            response.setPriceReferenceAt(latestPrice.getReferenceAt());
        }

        // 내부 제공처 식별자 대신 사용자에게 공개 가능한 표시명을 준다.
        response.setSource(new ProductDetailResponse.Source(
                SourceProviderLabel.of(product.getSourceProvider()),
                product.getSourceReferenceAt()
        ));

        return response;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse findPage(String assetType, String cursor, Integer size) {
        int pageSize = resolvePageSize(size);

        // 다음 페이지가 있는지 알려고 한 건 더 읽는다.
        // 비공개 상품은 여기서 제외된다 (active = true).
        List<FinancialProduct> found = financialProductMapper.findPage(
                parseAssetType(assetType),
                Boolean.TRUE,
                parseCursor(cursor),
                pageSize + 1
        );

        boolean hasNext = found.size() > pageSize;
        List<FinancialProduct> page = hasNext ? found.subList(0, pageSize) : found;

        List<ProductSummaryResponse> items = new ArrayList<>();

        for (FinancialProduct product : page) {
            items.add(toResponse(product));
        }

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getProductId())
                : null;

        return new ProductPageResponse(items, nextCursor);
    }

    private ProductSummaryResponse toResponse(FinancialProduct product) {
        ProductSummaryResponse response = new ProductSummaryResponse();

        response.setProductId(product.getProductId());
        response.setDisplayName(product.getDisplayName());
        response.setAssetType(product.getAssetType().name());
        response.setRiskLevel(product.getRiskLevel());

        // 압축 대상만 기간을 병기한다. 주식·ETF는 만기가 없어 생략한다.
        if (product.getAssetType().isTimeCompressed()) {
            response.setSimulationTerms(termsJsonCodec.read(product.getSimulationTermsJson()));
            response.setRealTerms(termsJsonCodec.read(product.getRealTermsJson()));
        }

        return response;
    }

    private static int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static AssetType parseAssetType(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            return null;
        }

        try {
            return AssetType.valueOf(assetType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_PRODUCT_FILTER);
        }
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_PRODUCT_FILTER);
        }
    }
}
