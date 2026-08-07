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
 * <p>인증은 필요하지만 <b>권한 조건은 없다.</b> {@code /api/admin/**}과 달리 역할을 따지지 않고,
 * 로그인한 사용자라면 학습 진도와 무관하게 모든 공개 상품을 볼 수 있다 (2026-08-05 팀 확정).
 * 이 서비스가 사용자 커리큘럼을 아예 참조하지 않는 것이 그 규칙을 지키는 방법이다.</p>
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
