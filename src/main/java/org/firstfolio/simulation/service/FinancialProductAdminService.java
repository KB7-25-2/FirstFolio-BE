package org.firstfolio.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.admin.dto.request.ProductUpdateRequest;
import org.firstfolio.admin.dto.response.AdminProductPageResponse;
import org.firstfolio.admin.dto.response.AdminProductResponse;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.firstfolio.simulation.mapper.FinancialProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자의 모의 상품 조회·수정 (FUNC-038).
 */
@Service
public class FinancialProductAdminService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    private final FinancialProductMapper financialProductMapper;

    public FinancialProductAdminService(FinancialProductMapper financialProductMapper) {
        this.financialProductMapper = financialProductMapper;
    }

    @Transactional(readOnly = true)
    public AdminProductPageResponse findPage(
            String assetType,
            String status,
            String cursor,
            Integer size
    ) {
        int pageSize = resolvePageSize(size);

        // 다음 페이지가 있는지 알려고 한 건 더 읽는다.
        List<FinancialProduct> found = financialProductMapper.findPage(
                parseAssetType(assetType),
                parseStatus(status),
                parseCursor(cursor),
                pageSize + 1
        );

        boolean hasNext = found.size() > pageSize;
        List<FinancialProduct> page = hasNext ? found.subList(0, pageSize) : found;

        List<AdminProductResponse> items = new ArrayList<>();

        for (FinancialProduct product : page) {
            items.add(toResponse(product));
        }

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getProductId())
                : null;

        return new AdminProductPageResponse(items, nextCursor);
    }

    @Transactional
    public AdminProductResponse update(Long productId, ProductUpdateRequest request) {
        FinancialProduct product = financialProductMapper.findById(productId);

        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        String displayName = request.getDisplayName() == null
                ? product.getDisplayName()
                : request.getDisplayName().trim();

        boolean activating = resolveActivation(request.getStatus(), product.isActive());

        if (activating) {
            requirePseudonym(displayName, product.getSourceProductName());
        }

        FinancialProduct changes = new FinancialProduct();

        changes.setProductId(productId);
        changes.setDisplayName(request.getDisplayName() == null ? null : displayName);
        changes.setDescription(request.getDescription());
        changes.setRiskLevel(request.getRiskLevel());
        changes.setSimulationTermsJson(
                request.getSimulationTerms() == null
                        ? null
                        : request.getSimulationTerms().toString()
        );
        changes.setActive(activating);
        changes.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        financialProductMapper.updateEditableFields(changes);

        return toResponse(financialProductMapper.findById(productId));
    }

    /**
     * 가명을 붙이지 않은 상품은 공개할 수 없다.
     *
     * <p>수집 직후 {@code display_name}에는 실제 상품명이 들어 있다. 비공개 상태라 노출되지는
     * 않지만, 이름을 바꾸지 않은 채 공개로 전환하면 그 순간 실제 상품명이 사용자에게 보인다
     * (FUNC-038: "실제 상품명·코드가 가명 상품명이나 공개 메타데이터에 남으면 공개할 수 없다").
     * 그래서 공개 전환 시점에 막는다.</p>
     */
    private void requirePseudonym(String displayName, String sourceProductName) {
        if (displayName == null || displayName.isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "가명 상품명을 입력해야 공개할 수 있습니다."
            );
        }

        if (sourceProductName != null
                && displayName.equalsIgnoreCase(sourceProductName.trim())) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "실제 상품명을 가명으로 사용할 수 없습니다. 가명을 입력한 뒤 공개하세요."
            );
        }
    }

    private boolean resolveActivation(String status, boolean current) {
        if (status == null) {
            return current;
        }

        String normalized = status.trim().toUpperCase();

        if (STATUS_ACTIVE.equals(normalized)) {
            return true;
        }

        if (STATUS_INACTIVE.equals(normalized)) {
            return false;
        }

        throw new ApiException(
                ErrorCode.INVALID_REQUEST,
                "status는 ACTIVE 또는 INACTIVE만 허용합니다."
        );
    }

    private AdminProductResponse toResponse(FinancialProduct product) {
        AdminProductResponse response = new AdminProductResponse();

        response.setProductId(product.getProductId());
        response.setDisplayName(product.getDisplayName());
        response.setAssetType(product.getAssetType() == null ? null : product.getAssetType().name());
        response.setDescription(product.getDescription());
        response.setRiskLevel(product.getRiskLevel());
        response.setSourceProvider(product.getSourceProvider());
        response.setSourceProductCode(product.getSourceProductCode());
        response.setSourceProductName(product.getSourceProductName());
        response.setSourceReferenceAt(product.getSourceReferenceAt());
        response.setRealTerms(readJson(product.getRealTermsJson()));
        response.setSimulationTerms(readJson(product.getSimulationTermsJson()));
        response.setStatus(product.isActive() ? STATUS_ACTIVE : STATUS_INACTIVE);

        return response;
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    "저장된 상품 조건을 읽을 수 없습니다.",
                    exception
            );
        }
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

    private static Boolean parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        String normalized = status.trim().toUpperCase();

        if (STATUS_ACTIVE.equals(normalized)) {
            return Boolean.TRUE;
        }

        if (STATUS_INACTIVE.equals(normalized)) {
            return Boolean.FALSE;
        }

        throw new ApiException(ErrorCode.INVALID_PRODUCT_FILTER);
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
