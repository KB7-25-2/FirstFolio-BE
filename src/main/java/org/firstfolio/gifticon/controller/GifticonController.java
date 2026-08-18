package org.firstfolio.gifticon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.gifticon.dto.request.GifticonExchangeRequest;
import org.firstfolio.gifticon.dto.response.GifticonCodeDisclosureResponse;
import org.firstfolio.gifticon.dto.response.GifticonExchangeResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductPageResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductResponse;
import org.firstfolio.gifticon.dto.response.MyGifticonPageResponse;
import org.firstfolio.gifticon.dto.response.MyGifticonResponse;
import org.firstfolio.gifticon.service.GifticonExchangeService;
import org.firstfolio.gifticon.service.GifticonMarketQueryService;
import org.firstfolio.gifticon.service.MyGifticonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
@Tag(name = "기프티콘 마켓", description = "포인트 교환과 내 기프티콘 조회 API")
public class GifticonController {

    private final GifticonMarketQueryService marketQueryService;
    private final GifticonExchangeService exchangeService;
    private final MyGifticonService myGifticonService;

    public GifticonController(
            GifticonMarketQueryService marketQueryService,
            GifticonExchangeService exchangeService,
            MyGifticonService myGifticonService
    ) {
        this.marketQueryService = marketQueryService;
        this.exchangeService = exchangeService;
        this.myGifticonService = myGifticonService;
    }

    @GetMapping("/gifticons")
    @Operation(
            summary = "기프티콘 마켓 상품 목록",
            description = "판매 중인 상품과 만료되지 않은 가용 코드 기준 재고를 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.GifticonProductPage.class
                            )
                    )
            )
    )
    public ApiResponse<GifticonProductPageResponse> findProducts(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(marketQueryService.findPage(
                currentUser.userId(), category, cursor, size
        ));
    }

    @GetMapping("/gifticons/{gifticon_product_id}")
    @Operation(
            summary = "기프티콘 마켓 상품 상세",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.GifticonProduct.class
                            )
                    )
            )
    )
    public ApiResponse<GifticonProductResponse> findProduct(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable("gifticon_product_id") long productId
    ) {
        return ApiResponse.of(marketQueryService.findById(
                currentUser.userId(), productId
        ));
    }

    @PostMapping("/gifticon-orders")
    @Operation(
            summary = "포인트로 기프티콘 교환",
            description = "포인트 차감, 코드 선점, 원장과 주문 생성을 한 트랜잭션으로 처리합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.GifticonExchange.class
                            )
                    )
            )
    )
    public ResponseEntity<ApiResponse<GifticonExchangeResponse>> exchange(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "사용자 범위 교환 멱등 키", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) GifticonExchangeRequest request
    ) {
        GifticonExchangeResponse response = exchangeService.exchange(
                currentUser.userId(), idempotencyKey, request
        );
        return ResponseEntity
                .status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.of(response));
    }

    @GetMapping("/gifticon-orders")
    @Operation(
            summary = "내 기프티콘 목록",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.MyGifticonPage.class
                            )
                    )
            )
    )
    public ApiResponse<MyGifticonPageResponse> findMine(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(myGifticonService.findPage(currentUser.userId(), cursor, size));
    }

    @GetMapping("/gifticon-orders/{gifticon_order_id}")
    @Operation(
            summary = "내 기프티콘 상세",
            description = "상세 조회에서도 실제 코드는 공개하지 않습니다. 공개 API를 명시적으로 호출해야 합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.MyGifticon.class
                            )
                    )
            )
    )
    public ApiResponse<MyGifticonResponse> findMineById(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable("gifticon_order_id") long orderId
    ) {
        return ApiResponse.of(myGifticonService.findById(currentUser.userId(), orderId));
    }

    @PostMapping("/gifticon-orders/{gifticon_order_id}/disclosures")
    @Operation(
            summary = "내 기프티콘 코드 공개",
            description = "본인 주문을 확인한 뒤 코드를 복호화하고 공개 이력을 남깁니다. 바코드는 프론트에서 렌더링합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = OpenApiResponseSchemas.GifticonCodeDisclosure.class
                            )
                    )
            )
    )
    public ResponseEntity<ApiResponse<GifticonCodeDisclosureResponse>> disclose(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable("gifticon_order_id") long orderId,
            HttpServletRequest servletRequest
    ) {
        GifticonCodeDisclosureResponse response = myGifticonService.disclose(
                currentUser.userId(), orderId,
                RequestIdFilter.currentRequestId(servletRequest)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.of(response));
    }
}
