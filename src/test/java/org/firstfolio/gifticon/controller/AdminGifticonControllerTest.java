package org.firstfolio.gifticon.controller;

import org.firstfolio.admin.controller.AdminGifticonController;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeBatchResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonProductResponse;
import org.firstfolio.gifticon.service.GifticonCodeAdminService;
import org.firstfolio.gifticon.service.GifticonProductAdminService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGifticonControllerTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(
            900L, "firebase-admin", "관리자", UserRole.ADMIN
    );

    private GifticonProductAdminService productService;
    private GifticonCodeAdminService codeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(GifticonProductAdminService.class);
        codeService = mock(GifticonCodeAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminGifticonController(productService, codeService)
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
    void createsProductFromSnakeCaseBody() throws Exception {
        when(productService.create(any(), eq(900L), anyString())).thenReturn(
                new AdminGifticonProductResponse(
                        11L, "아메리카노", "카페", "CAFE", 4500, 4500,
                        "STOPPED", "SOLD_OUT", 0, 0, 0, null,
                        LocalDateTime.of(2026, 8, 18, 1, 0),
                        LocalDateTime.of(2026, 8, 18, 1, 0)
                )
        );

        mockMvc.perform(post("/api/admin/gifticons")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "아메리카노",
                                  "brand_name": "카페",
                                  "category": "CAFE",
                                  "face_value_krw": 4500
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gifticon_product_id").value(11))
                .andExpect(jsonPath("$.data.required_points").value(4500))
                .andExpect(jsonPath("$.data.stock_status").value("SOLD_OUT"));
    }

    @Test
    void acceptsCodeOnlyAndReturnsMaskedCodeOnly() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 1, 0, 0);
        when(codeService.createBatch(eq(11L), any(), eq(900L), anyString())).thenReturn(
                new AdminGifticonCodeBatchResponse(1, List.of(
                        new AdminGifticonCodeResponse(
                                100L, 11L, "********ABCD", "AVAILABLE", expiresAt,
                                LocalDateTime.of(2026, 8, 18, 1, 0)
                        )
                ))
        );
        ArgumentCaptor<org.firstfolio.gifticon.dto.request.GifticonCodeBatchCreateRequest> captor =
                ArgumentCaptor.forClass(
                        org.firstfolio.gifticon.dto.request.GifticonCodeBatchCreateRequest.class
                );

        mockMvc.perform(post("/api/admin/gifticons/11/codes")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [{
                                    "code": "1234-5678-ABCD",
                                    "expires_at": "2026-09-01T00:00:00Z"
                                  }]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created_count").value(1))
                .andExpect(jsonPath("$.data.items[0].code_masked").value("********ABCD"))
                .andExpect(jsonPath("$.data.items[0].code").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].code_ciphertext").doesNotExist());

        verify(codeService).createBatch(eq(11L), captor.capture(), eq(900L), anyString());
        assertEquals("1234-5678-ABCD", captor.getValue().items().get(0).code());
        assertEquals(expiresAt, captor.getValue().items().get(0).expiresAt());
    }
}
