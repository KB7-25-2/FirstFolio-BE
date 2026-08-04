package org.firstfolio.admin.controller;

import org.firstfolio.admin.dto.request.ProductImportRequest;
import org.firstfolio.admin.dto.request.ProductUpdateRequest;
import org.firstfolio.admin.dto.response.AdminProductPageResponse;
import org.firstfolio.admin.dto.response.AdminProductResponse;
import org.firstfolio.admin.dto.response.ProductImportResponse;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.service.FinancialProductAdminService;
import org.firstfolio.simulation.service.FinancialProductImportService;
import org.firstfolio.simulation.service.ProductImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 모의 상품 API (FUNC-038).
 *
 * <p>ADMIN 권한 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/admin/**}). 컨트롤러마다 권한 검사를 반복하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/admin/financial-products")
public class AdminFinancialProductController {

    private final FinancialProductImportService importService;
    private final FinancialProductAdminService adminService;

    public AdminFinancialProductController(
            FinancialProductImportService importService,
            FinancialProductAdminService adminService
    ) {
        this.importService = importService;
        this.adminService = adminService;
    }

    /**
     * 원천 데이터에서 상품을 가져와 <b>비공개 상태로</b> 등록한다.
     * 가명은 이후 {@link #update}로 입력한다.
     */
    @PostMapping("/imports")
    public ResponseEntity<ApiResponse<ProductImportResponse>> importProducts(
            @RequestBody ProductImportRequest request
    ) {
        if (request.getSourceProvider() == null || request.getSourceProvider().isBlank()) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "source_provider는 필수입니다."
            );
        }

        if (request.getReferenceAt() == null) {
            throw new ApiException(
                    ErrorCode.INVALID_SOURCE_PRODUCT,
                    "reference_at은 필수입니다."
            );
        }

        ProductImportResult result = importService.importProducts(
                request.getSourceProvider().trim(),
                request.getReferenceAt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(new ProductImportResponse(result)));
    }

    @GetMapping
    public ApiResponse<AdminProductPageResponse> findPage(
            @RequestParam(value = "asset_type", required = false) String assetType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(adminService.findPage(assetType, status, cursor, size));
    }

    @PatchMapping("/{product_id}")
    public ApiResponse<AdminProductResponse> update(
            @PathVariable("product_id") Long productId,
            @RequestBody ProductUpdateRequest request
    ) {
        return ApiResponse.of(adminService.update(productId, request));
    }
}
