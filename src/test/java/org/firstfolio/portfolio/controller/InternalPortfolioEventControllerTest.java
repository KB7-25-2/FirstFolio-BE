package org.firstfolio.portfolio.controller;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.dto.response.PortfolioEventProcessResponse;
import org.firstfolio.portfolio.service.PortfolioEventBatchResult;
import org.firstfolio.portfolio.service.PortfolioEventResult;
import org.firstfolio.portfolio.service.PortfolioEventService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청·응답 표기를 확인한다 (FUNC-041).
 *
 * <p>내부 호출 토큰 검증은 {@code ServletConfig}의 경로 인터셉터가 하므로 여기서는 다루지 않는다
 * (경로 등록은 {@code ServletConfigInterceptorPathTest}가 검증한다).</p>
 */
class InternalPortfolioEventControllerTest {

    private static final LocalDateTime PROCESS_UNTIL = LocalDateTime.of(2026, 7, 29, 3, 20, 0);
    private static final String EVENT_KEY = "interest-8101-8201-20260729T0300Z";

    private PortfolioEventService portfolioEventService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        portfolioEventService = mock(PortfolioEventService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalPortfolioEventController(portfolioEventService))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("처리 결과를 처리·완료·실패 건수로 돌려준다")
    void returnsBatchCounts() throws Exception {
        when(portfolioEventService.process(any(), any()))
                .thenReturn(new PortfolioEventBatchResult(480, 478, 2, 0));

        mockMvc.perform(post("/api/internal/portfolio-events/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"process_until\":\"2026-07-29T03:20:00Z\",\"batch_size\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed_count").value(480))
                .andExpect(jsonPath("$.data.completed_count").value(478))
                .andExpect(jsonPath("$.data.failed_count").value(2));
    }

    @Test
    @DisplayName("next_cursor는 언제나 null이지만 필드를 생략하지 않는다")
    void alwaysIncludesNextCursorField() throws Exception {
        String json = ApiObjectMapperFactory.create().writeValueAsString(
                new PortfolioEventProcessResponse(new PortfolioEventBatchResult(0, 0, 0, 0)));

        // 필드를 빼면 FE가 이미 짠 파싱이 깨진다 — 명세가 null을 명시하는 필드다.
        assertTrue(json.contains("\"next_cursor\":null"), json);
    }

    @Test
    @DisplayName("기준 시각과 건수를 그대로 서비스에 넘긴다")
    void passesRequestToService() throws Exception {
        when(portfolioEventService.process(any(), any()))
                .thenReturn(new PortfolioEventBatchResult(0, 0, 0, 0));

        mockMvc.perform(post("/api/internal/portfolio-events/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"process_until\":\"2026-07-29T03:20:00Z\",\"batch_size\":100}"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> processUntil = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> batchSize = ArgumentCaptor.forClass(Integer.class);

        verify(portfolioEventService).process(processUntil.capture(), batchSize.capture());

        // "...Z"가 UTC LocalDateTime으로 들어와야 한다.
        assertEquals(PROCESS_UNTIL, processUntil.getValue());
        assertEquals(100, batchSize.getValue());
    }

    @Test
    @DisplayName("건수를 비우면 서비스가 기본값을 쓰도록 null로 넘긴다")
    void passesNullBatchSizeWhenOmitted() throws Exception {
        when(portfolioEventService.process(any(), any()))
                .thenReturn(new PortfolioEventBatchResult(0, 0, 0, 0));

        mockMvc.perform(post("/api/internal/portfolio-events/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"process_until\":\"2026-07-29T03:20:00Z\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> batchSize = ArgumentCaptor.forClass(Integer.class);

        verify(portfolioEventService).process(any(), batchSize.capture());

        assertNull(batchSize.getValue());
    }

    @Test
    @DisplayName("미래 기준 시각은 400으로 나간다")
    void translatesFutureProcessUntilToError() throws Exception {
        when(portfolioEventService.process(any(), any()))
                .thenThrow(new ApiException(
                        ErrorCode.INVALID_REQUEST, "미래 시점의 이벤트는 처리할 수 없습니다."));

        mockMvc.perform(post("/api/internal/portfolio-events/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"process_until\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정의되지 않은 필드가 오면 거부한다")
    void rejectsUnknownField() throws Exception {
        mockMvc.perform(post("/api/internal/portfolio-events/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"process_until\":\"2026-07-29T03:20:00Z\",\"retry\":true}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 재처리

    @Test
    @DisplayName("재처리는 본문 없이 경로의 이벤트 키만으로 호출한다")
    void retriesByEventKeyAlone() throws Exception {
        when(portfolioEventService.retry(anyString())).thenReturn(new PortfolioEventResult(
                EVENT_KEY, "COMPLETED", 8301L, LocalDateTime.of(2026, 7, 29, 3, 22, 0)));

        mockMvc.perform(post("/api/internal/portfolio-events/" + EVENT_KEY + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_key").value(EVENT_KEY))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.portfolio_transaction_id").value(8301))
                .andExpect(jsonPath("$.data.processed_at").value("2026-07-29T03:22:00Z"));

        verify(portfolioEventService).retry(EVENT_KEY);
    }

    @Test
    @DisplayName("다시 실패해도 200으로 FAILED 상태를 알려준다")
    void returnsFailedStatusWithOk() throws Exception {
        when(portfolioEventService.retry(anyString()))
                .thenReturn(new PortfolioEventResult(EVENT_KEY, "FAILED", 8301L, null));

        mockMvc.perform(post("/api/internal/portfolio-events/" + EVENT_KEY + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.processed_at").isEmpty());
    }

    @Test
    @DisplayName("없는 이벤트는 404다")
    void translatesUnknownEventToNotFound() throws Exception {
        doThrow(new ApiException(ErrorCode.EVENT_NOT_FOUND))
                .when(portfolioEventService).retry(anyString());

        mockMvc.perform(post("/api/internal/portfolio-events/interest-9999-1-20260729T0300Z/retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("재처리할 수 없는 상태는 409다")
    void translatesNonRetryableEventToConflict() throws Exception {
        doThrow(new ApiException(ErrorCode.EVENT_NOT_RETRYABLE))
                .when(portfolioEventService).retry(anyString());

        mockMvc.perform(post("/api/internal/portfolio-events/" + EVENT_KEY + "/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_RETRYABLE"));
    }
}
