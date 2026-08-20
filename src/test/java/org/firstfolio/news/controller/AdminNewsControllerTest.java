package org.firstfolio.news.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.news.dto.request.NewsPatchRequest;
import org.firstfolio.news.dto.response.FinancialNewsDeleteResponse;
import org.firstfolio.news.dto.response.FinancialNewsItemResponse;
import org.firstfolio.news.service.NewsService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNewsControllerTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(
            900L,
            "firebase-admin",
            "관리자",
            UserRole.ADMIN
    );

    private NewsService newsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        newsService = mock(NewsService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminNewsController(newsService))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .build();
    }

    @Test
    void patchesNewsAndReturnsUpdatedItem() throws Exception {
        when(newsService.updateArticle(eq(1L), any(NewsPatchRequest.class)))
                .thenReturn(item());

        mockMvc.perform(patch("/api/admin/financial-news/1")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "예·적금 금리 비교 수요 증가…은행권 경쟁 격화",
                                  "summary": "기준금리 동결 결정의 배경과 예·적금, 대출 금리에 미칠 영향을 요약합니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financial_news_id").value(1))
                .andExpect(jsonPath("$.data.title")
                        .value("예·적금 금리 비교 수요 증가…은행권 경쟁 격화"))
                .andExpect(jsonPath("$.data.source_name").value("경제일보"));

        verify(newsService).updateArticle(eq(1L), any(NewsPatchRequest.class));
    }

    @Test
    void deletesNewsAndReturnsId() throws Exception {
        when(newsService.deleteArticle(1L))
                .thenReturn(new FinancialNewsDeleteResponse(1L));

        mockMvc.perform(delete("/api/admin/financial-news/1")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financial_news_id").value(1));
    }

    @Test
    void returnsNotFoundWhenNewsMissing() throws Exception {
        when(newsService.updateArticle(eq(99L), any()))
                .thenThrow(new ApiException(ErrorCode.FINANCIAL_NEWS_NOT_FOUND));
        when(newsService.deleteArticle(99L))
                .thenThrow(new ApiException(ErrorCode.FINANCIAL_NEWS_NOT_FOUND));

        mockMvc.perform(patch("/api/admin/financial-news/99")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 제목\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FINANCIAL_NEWS_NOT_FOUND"));

        mockMvc.perform(delete("/api/admin/financial-news/99")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FINANCIAL_NEWS_NOT_FOUND"));
    }

    private FinancialNewsItemResponse item() {
        return new FinancialNewsItemResponse(
                1L,
                "예·적금 금리 비교 수요 증가…은행권 경쟁 격화",
                "기준금리 동결 결정의 배경과 예·적금, 대출 금리에 미칠 영향을 요약합니다.",
                null,
                "경제일보",
                "https://example.com/source-news",
                LocalDateTime.of(2026, 8, 16, 9, 0),
                LocalDateTime.of(2026, 8, 17, 9, 0)
        );
    }
}
