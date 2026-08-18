package org.firstfolio.gifticon.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.gifticon.dto.response.GifticonCodeDisclosureResponse;
import org.firstfolio.gifticon.dto.response.GifticonExchangeResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductListItemResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductPageResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductResponse;
import org.firstfolio.gifticon.dto.response.MyGifticonResponse;
import org.firstfolio.gifticon.service.GifticonExchangeService;
import org.firstfolio.gifticon.service.GifticonMarketQueryService;
import org.firstfolio.gifticon.service.MyGifticonService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GifticonControllerTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(
            101L, "firebase-user", "학생", UserRole.USER
    );
    private static final LocalDateTime COMPLETED_AT =
            LocalDateTime.of(2026, 8, 18, 7, 30);

    private GifticonMarketQueryService marketService;
    private GifticonExchangeService exchangeService;
    private MyGifticonService myService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        marketService = mock(GifticonMarketQueryService.class);
        exchangeService = mock(GifticonExchangeService.class);
        myService = mock(MyGifticonService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new GifticonController(marketService, exchangeService, myService)
                )
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    void productListContainsBalanceButNotExactInventoryQuantity() throws Exception {
        when(marketService.findPage(101L, "CAFE", null, null)).thenReturn(
                new GifticonProductPageResponse(7200, List.of(
                        new GifticonProductListItemResponse(
                                11L, "아메리카노 교환권", "스타카페", "CAFE",
                                5000, 5000, "AVAILABLE", true, "https://image"
                        )
                ), null)
        );

        mockMvc.perform(get("/api/gifticons")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, USER)
                        .param("category", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.point_balance").value(7200))
                .andExpect(jsonPath("$.data.items[0].stock_status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.items[0].can_exchange").value(true))
                .andExpect(jsonPath("$.data.items[0].available_quantity").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].point_balance").doesNotExist());
    }

    @Test
    void firstExchangeIsCreatedAndReplayIsOk() throws Exception {
        GifticonExchangeResponse created = new GifticonExchangeResponse(
                501L, 11L, 5000, 2200, COMPLETED_AT, false
        );
        GifticonExchangeResponse replay = new GifticonExchangeResponse(
                501L, 11L, 5000, 2200, COMPLETED_AT, true
        );
        when(exchangeService.exchange(eq(101L), eq("exchange-1"), any()))
                .thenReturn(created, replay);

        var request = post("/api/gifticon-orders")
                .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, USER)
                .header("Idempotency-Key", "exchange-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gifticon_product_id\":11}");

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gifticon_order_id").value(501))
                .andExpect(jsonPath("$.data.point_balance").value(2200))
                .andExpect(jsonPath("$.data.idempotent_replay").value(false))
                .andExpect(jsonPath("$.data.code").doesNotExist());

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").value(true));
    }

    @Test
    void detailDoesNotDiscloseCode() throws Exception {
        when(myService.findById(101L, 501L)).thenReturn(new MyGifticonResponse(
                501L, 11L, "스타카페", "아메리카노 교환권", "CAFE",
                5000, 5000, "https://image", "********9012",
                LocalDateTime.of(2027, 2, 28, 14, 59, 59), null, COMPLETED_AT
        ));

        mockMvc.perform(get("/api/gifticon-orders/501")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product_name").value("아메리카노 교환권"))
                .andExpect(jsonPath("$.data.code_masked").value("********9012"))
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andExpect(jsonPath("$.data.barcode_value").doesNotExist());
    }

    @Test
    void disclosureReturnsCodeWithNoStoreHeaders() throws Exception {
        when(myService.disclose(eq(101L), eq(501L), anyString())).thenReturn(
                new GifticonCodeDisclosureResponse(
                        501L, "1234-5678-9012", "123456789012", "CODE_128",
                        LocalDateTime.of(2027, 2, 28, 14, 59, 59), false,
                        LocalDateTime.of(2026, 8, 18, 7, 35)
                )
        );

        mockMvc.perform(post("/api/gifticon-orders/501/disclosures")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, USER))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.data.code").value("1234-5678-9012"))
                .andExpect(jsonPath("$.data.barcode_value").value("123456789012"))
                .andExpect(jsonPath("$.data.barcode_format").value("CODE_128"));
    }
}
