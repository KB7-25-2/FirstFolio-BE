package org.firstfolio.simulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
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
@Tag(name = "모의 금융상품", description = "학습 완료 여부와 무관하게 조회할 수 있는 가명 모의 금융상품 API")
public class FinancialProductController {

    private final FinancialProductQueryService queryService;

    public FinancialProductController(FinancialProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "공개 모의 상품 목록 조회",
            description = "선택 가능한 공개 가명 모의 상품을 커서 방식으로 조회합니다. 실제 상품명과 원상품 식별자는 노출하지 않으며, "
                    + "시간 압축 대상은 서비스 조건과 실제 조건을 함께 제공합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "모의 상품 목록 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ProductPage.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_PRODUCT_FILTER - 상품 필터가 올바르지 않음"
                    )
            }
    )
    public ApiResponse<ProductPageResponse> findPage(
            @Parameter(
                    description = "자산군 필터",
                    example = "BOND"
            )
            @RequestParam(value = "asset_type", required = false) String assetType,
            @Parameter(description = "다음 페이지 조회용 불투명 커서")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(queryService.findPage(assetType, cursor, size));
    }

    @GetMapping("/{product_id}")
    @Operation(
            summary = "공개 모의 상품 상세 조회",
            description = "가명 모의 상품의 상세 조건과 마지막 유효 기준 가격을 조회합니다. 사용자에게 공개 가능한 제공처와 기준 시점만 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "모의 상품 상세 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ProductDetail.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "PRODUCT_NOT_FOUND - 공개 상품을 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<ProductDetailResponse> findById(
            @Parameter(description = "모의 상품 ID", example = "25", required = true)
            @PathVariable("product_id") Long productId
    ) {
        return ApiResponse.of(queryService.findById(productId));
    }
}
