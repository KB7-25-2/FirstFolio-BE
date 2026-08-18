package org.firstfolio.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.gifticon.dto.request.GifticonCodeBatchCreateRequest;
import org.firstfolio.gifticon.dto.request.GifticonCodeVoidRequest;
import org.firstfolio.gifticon.dto.request.GifticonProductCreateRequest;
import org.firstfolio.gifticon.dto.request.GifticonProductPatchRequest;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeBatchResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodePageResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeVoidResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonProductPageResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonProductResponse;
import org.firstfolio.gifticon.service.GifticonCodeAdminService;
import org.firstfolio.gifticon.service.GifticonProductAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "관리자 기프티콘 재고", description = "기프티콘 상품과 선구매 코드 재고 관리 API")
public class AdminGifticonController {

    private final GifticonProductAdminService productService;
    private final GifticonCodeAdminService codeService;

    public AdminGifticonController(
            GifticonProductAdminService productService,
            GifticonCodeAdminService codeService
    ) {
        this.productService = productService;
        this.codeService = codeService;
    }

    @GetMapping("/gifticons")
    @Operation(
            summary = "기프티콘 상품 목록 조회",
            description = "상품과 사용 가능한 코드 재고 수를 커서 방식으로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.AdminGifticonProductPage.class
                            )
                    )
            )
    )
    public ApiResponse<AdminGifticonProductPageResponse> findProducts(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(productService.findPage(status, cursor, size));
    }

    @PostMapping("/gifticons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "기프티콘 상품 생성",
            description = "필요 포인트는 액면가와 같은 값으로 서버가 저장합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.AdminGifticonProduct.class
                            )
                    )
            )
    )
    public ApiResponse<AdminGifticonProductResponse> createProduct(
            @RequestBody(required = false) GifticonProductCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(productService.create(
                request, currentUser.userId(), RequestIdFilter.currentRequestId(servletRequest)
        ));
    }

    @PatchMapping("/gifticons/{gifticon_product_id}")
    @Operation(
            summary = "기프티콘 상품 수정",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.AdminGifticonProduct.class
                            )
                    )
            )
    )
    public ApiResponse<AdminGifticonProductResponse> patchProduct(
            @Parameter(description = "기프티콘 상품 ID")
            @PathVariable("gifticon_product_id") long productId,
            @RequestBody(required = false) GifticonProductPatchRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(productService.patch(
                productId, request, currentUser.userId(), RequestIdFilter.currentRequestId(servletRequest)
        ));
    }

    @GetMapping("/gifticons/{gifticon_product_id}/codes")
    @Operation(
            summary = "개별 기프티콘 코드 재고 조회",
            description = "평문이나 암호문 없이 마스킹 코드만 반환합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.AdminGifticonCodePage.class
                            )
                    )
            )
    )
    public ApiResponse<AdminGifticonCodePageResponse> findCodes(
            @PathVariable("gifticon_product_id") long productId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "expires_before", required = false) String expiresBefore,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(codeService.findPage(
                productId, status, expiresBefore, cursor, size
        ));
    }

    @PostMapping("/gifticons/{gifticon_product_id}/codes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "개별 기프티콘 코드 일괄 등록",
            description = "최대 100개 코드를 한 트랜잭션으로 등록합니다. 코드는 암호화하고 중복 방지 지문을 별도로 저장합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.AdminGifticonCodeBatch.class
                            )
                    )
            )
    )
    public ApiResponse<AdminGifticonCodeBatchResponse> createCodes(
            @PathVariable("gifticon_product_id") long productId,
            @RequestBody(required = false) GifticonCodeBatchCreateRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(codeService.createBatch(
                productId, request, currentUser.userId(), RequestIdFilter.currentRequestId(servletRequest)
        ));
    }

    @PostMapping("/gifticon-codes/{gifticon_code_id}/void")
    @Operation(
            summary = "미지급 기프티콘 코드 폐기",
            description = "AVAILABLE 코드만 폐기할 수 있으며 지급된 코드는 변경하지 않습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.AdminGifticonCodeVoid.class
                            )
                    )
            )
    )
    public ApiResponse<AdminGifticonCodeVoidResponse> voidCode(
            @PathVariable("gifticon_code_id") long codeId,
            @RequestBody(required = false) GifticonCodeVoidRequest request,
            @CurrentUser AuthenticatedUser currentUser,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.of(codeService.voidCode(
                codeId, request, currentUser.userId(), RequestIdFilter.currentRequestId(servletRequest)
        ));
    }
}
