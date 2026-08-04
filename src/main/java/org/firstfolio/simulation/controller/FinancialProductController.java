package org.firstfolio.simulation.controller;

import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.simulation.dto.response.ProductDetailResponse;
import org.firstfolio.simulation.dto.response.ProductPageResponse;
import org.firstfolio.simulation.service.FinancialProductQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자용 모의 상품 API (FUNC-031).
 *
 * <p>{@code /api/admin/**}과 달리 권한 인터셉터가 걸리지 않는다. 공개 상품 목록은
 * 학습 완료 여부와 무관하게 누구나 볼 수 있어야 한다.</p>
 */
@RestController
@RequestMapping("/api/financial-products")
public class FinancialProductController {

    private final FinancialProductQueryService queryService;

    public FinancialProductController(FinancialProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<ProductPageResponse> findPage(
            @RequestParam(value = "asset_type", required = false) String assetType,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(queryService.findPage(assetType, cursor, size));
    }

    @GetMapping("/{product_id}")
    public ApiResponse<ProductDetailResponse> findById(
            @PathVariable("product_id") Long productId
    ) {
        return ApiResponse.of(queryService.findById(productId));
    }
}
