package org.firstfolio.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.admin.dto.request.ProductImportRequest;
import org.firstfolio.admin.dto.request.ProductUpdateRequest;
import org.firstfolio.admin.dto.response.AdminProductPageResponse;
import org.firstfolio.admin.dto.response.AdminProductResponse;
import org.firstfolio.admin.dto.response.ProductImportResponse;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
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
@Tag(name = "관리자 모의 금융상품", description = "관리자용 원상품 수집·가명 상품 조회·수정 API")
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
    @Operation(
            summary = "원천 금융상품 가져오기",
            description = "서버가 지정 제공처의 외부 API를 호출해 원천 상품을 수집하고 비공개 상태로 등록합니다. "
                    + "가명과 공개 상태는 등록 후 수정 API에서 설정합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "모의 상품 가져오기 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.ProductImport.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "INVALID_SOURCE_PRODUCT - 제공처 또는 기준 시점이 올바르지 않음"
                    )
            }
    )
    public ResponseEntity<ApiResponse<ProductImportResponse>> importProducts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "서버가 조회할 원천 데이터 제공처와 기준 시점"
            )
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
    @Operation(
            summary = "관리자 상품 목록 조회",
            description = "가명 상품과 관리자 전용 원상품 식별 정보·출처·실제 조건·시뮬레이션 조건을 커서 방식으로 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "관리자 모의 상품 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.AdminProductPage.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    )
            }
    )
    public ApiResponse<AdminProductPageResponse> findPage(
            @Parameter(description = "자산군 필터", example = "BOND")
            @RequestParam(value = "asset_type", required = false) String assetType,
            @Parameter(description = "상품 상태 필터", example = "ACTIVE")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "다음 페이지 조회용 불투명 커서")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(adminService.findPage(assetType, status, cursor, size));
    }

    @PatchMapping("/{product_id}")
    @Operation(
            summary = "관리자 상품 수정",
            description = "가명 상품명·설명·위험도·시뮬레이션 조건·공개 상태 중 전달된 필드만 수정합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "모의 상품 수정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.AdminProduct.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ADMIN_REQUIRED - 관리자 권한 필요"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "PRODUCT_NOT_FOUND - 상품을 찾을 수 없음"
                    )
            }
    )
    public ApiResponse<AdminProductResponse> update(
            @Parameter(description = "모의 상품 ID", example = "25", required = true)
            @PathVariable("product_id") Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "수정할 가명 상품 필드"
            )
            @RequestBody ProductUpdateRequest request
    ) {
        return ApiResponse.of(adminService.update(productId, request));
    }
}
