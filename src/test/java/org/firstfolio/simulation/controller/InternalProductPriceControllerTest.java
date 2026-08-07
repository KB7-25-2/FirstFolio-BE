package org.firstfolio.simulation.controller;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.service.PriceRefreshResult;
import org.firstfolio.simulation.service.PriceRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청·응답 표기를 확인한다.
 *
 * <p>내부 호출 토큰 검증은 {@code ServletConfig}의 경로 인터셉터가 하므로 여기서는 다루지 않는다
 * (경로 등록은 {@code ServletConfigInterceptorPathTest}가 검증한다).</p>
 */
class InternalProductPriceControllerTest {

    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 7, 29, 3, 15, 0);

    private PriceRefreshService priceRefreshService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        priceRefreshService = mock(PriceRefreshService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalProductPriceController(priceRefreshService))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private void givenResult(int processed, int created, int skipped) {
        when(priceRefreshService.refresh(any(), any()))
                .thenReturn(new PriceRefreshResult(REFERENCE_AT, processed, created, skipped));
    }

    @Test
    @DisplayName("기준 시점과 상품 목록을 받아 갱신 결과를 돌려준다")
    void refreshesGivenProducts() throws Exception {
        givenResult(2, 2, 0);

        mockMvc.perform(post("/api/internal/product-prices/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference_at\":\"2026-07-29T03:15:00Z\",\"product_ids\":[31,32]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference_at").value("2026-07-29T03:15:00Z"))
                .andExpect(jsonPath("$.data.processed_count").value(2))
                .andExpect(jsonPath("$.data.created_count").value(2))
                .andExpect(jsonPath("$.data.skipped_count").value(0));
    }

    @Test
    @DisplayName("요청의 기준 시점과 상품 목록을 그대로 서비스에 넘긴다")
    void passesRequestToService() throws Exception {
        givenResult(2, 2, 0);

        mockMvc.perform(post("/api/internal/product-prices/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference_at\":\"2026-07-29T03:15:00Z\",\"product_ids\":[31,32]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> referenceAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<List<Long>> productIds = ArgumentCaptor.forClass(List.class);

        verify(priceRefreshService).refresh(referenceAt.capture(), productIds.capture());

        // "...Z"가 UTC LocalDateTime으로 들어와야 한다.
        assertEquals(REFERENCE_AT, referenceAt.getValue());
        assertEquals(List.of(31L, 32L), productIds.getValue());
    }

    @Test
    @DisplayName("상품 목록을 비우면 전체 갱신으로 넘긴다")
    void treatsMissingProductIdsAsAll() throws Exception {
        givenResult(15, 15, 0);

        mockMvc.perform(post("/api/internal/product-prices/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference_at\":\"2026-07-29T03:15:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed_count").value(15));

        ArgumentCaptor<List<Long>> productIds = ArgumentCaptor.forClass(List.class);

        verify(priceRefreshService).refresh(any(), productIds.capture());

        assertNull(productIds.getValue(), "비어 있으면 전체가 대상입니다.");
    }

    @Test
    @DisplayName("정책에 어긋나는 기준 시점은 422로 나간다")
    void translatesPolicyViolationToError() throws Exception {
        when(priceRefreshService.refresh(any(), any()))
                .thenThrow(new ApiException(
                        ErrorCode.PRICE_POLICY_INVALID,
                        "미래 시점의 가격은 만들 수 없습니다."
                ));

        mockMvc.perform(post("/api/internal/product-prices/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference_at\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PRICE_POLICY_INVALID"));
    }

    @Test
    @DisplayName("정의되지 않은 필드가 오면 거부한다")
    void rejectsUnknownField() throws Exception {
        mockMvc.perform(post("/api/internal/product-prices/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference_at\":\"2026-07-29T03:15:00Z\",\"interval\":60}"))
                .andExpect(status().isBadRequest());
    }
}
