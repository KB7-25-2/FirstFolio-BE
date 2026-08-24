package org.firstfolio.newsletter.controller;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.newsletter.domain.Newsletter;
import org.firstfolio.newsletter.domain.NewsletterGenerationType;
import org.firstfolio.newsletter.service.NewsletterCreateService;
import org.firstfolio.newsletter.service.NewsletterPublicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/internal/newsletters/{id}/publish}는 로그인 세션 없이 자동화 스크립트가
 * 부르므로, 감사 로그 행위자를 {@code newsletter.batch.ai-created-by} 시스템 계정으로
 * 고정해서 호출하는지 확인한다.
 */
class InternalNewsletterControllerTest {

    private static final String AI_CREATED_BY_PROPERTY = "newsletter.batch.ai-created-by";

    private NewsletterCreateService newsletterCreateService;
    private NewsletterPublicationService newsletterPublicationService;
    private Environment environment;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        newsletterCreateService = mock(NewsletterCreateService.class);
        newsletterPublicationService = mock(NewsletterPublicationService.class);
        environment = mock(Environment.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalNewsletterController(
                        newsletterCreateService,
                        newsletterPublicationService,
                        environment
                ))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("시스템 계정 ID로 발행 서비스를 호출한다")
    void publishesWithSystemActorId() throws Exception {
        when(environment.getProperty(AI_CREATED_BY_PROPERTY)).thenReturn("1");
        when(newsletterPublicationService.publish(eq(5L), eq(1L), anyString()))
                .thenReturn(publishedNewsletter(5L));

        mockMvc.perform(post("/api/internal/newsletters/5/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsletter_id").value(5))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        verify(newsletterPublicationService).publish(eq(5L), eq(1L), anyString());
    }

    @Test
    @DisplayName("시스템 계정 설정이 없으면 500으로 거부한다")
    void rejectsWhenSystemActorIdMissing() throws Exception {
        when(environment.getProperty(AI_CREATED_BY_PROPERTY)).thenReturn(null);

        mockMvc.perform(post("/api/internal/newsletters/5/publish"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("REVIEW 상태가 아니면 409를 그대로 전달한다")
    void translatesNotPublishableToConflict() throws Exception {
        when(environment.getProperty(AI_CREATED_BY_PROPERTY)).thenReturn("1");
        doThrow(new ApiException(ErrorCode.NEWSLETTER_NOT_PUBLISHABLE))
                .when(newsletterPublicationService).publish(anyLong(), anyLong(), anyString());

        mockMvc.perform(post("/api/internal/newsletters/5/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NEWSLETTER_NOT_PUBLISHABLE"));
    }

    private static Newsletter publishedNewsletter(long newsletterId) {
        Newsletter newsletter = Newsletter.review(
                LocalDate.of(2026, 8, 17),
                "대제목",
                "[]",
                "[]",
                "[]",
                NewsletterGenerationType.AI,
                1L,
                LocalDateTime.of(2026, 8, 20, 5, 0)
        );
        newsletter.setNewsletterId(newsletterId);
        newsletter.publish(LocalDateTime.of(2026, 8, 20, 6, 0));
        return newsletter;
    }
}
