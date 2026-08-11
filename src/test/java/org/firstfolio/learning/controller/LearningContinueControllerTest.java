package org.firstfolio.learning.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningContinueResult;
import org.firstfolio.learning.service.LearningContinueService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningContinueControllerTest {

    private static final long USER_ID = 11L;

    private LearningContinueService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LearningContinueService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LearningContinueController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsContinuePositionForCurrentUser() throws Exception {
        when(service.getContinuePosition(USER_ID)).thenReturn(
                new LearningContinueResult(
                        502L,
                        2L,
                        101L,
                        301L,
                        "page-2",
                        50,
                        "/learning/sub-chapters/101?page=page-2"
                )
        );

        mockMvc.perform(authenticated(get("/api/learning/continue")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curriculum_item_id").value(502))
                .andExpect(jsonPath("$.data.main_chapter_id").value(2))
                .andExpect(jsonPath("$.data.sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.content_version_id").value(301))
                .andExpect(jsonPath("$.data.last_page_id").value("page-2"))
                .andExpect(jsonPath("$.data.progress_percent").value(50))
                .andExpect(jsonPath("$.data.route").value(
                        "/learning/sub-chapters/101?page=page-2"
                ));

        verify(service).getContinuePosition(USER_ID);
    }

    @Test
    void returnsNotFoundWhenThereIsNoContinuePosition() throws Exception {
        when(service.getContinuePosition(USER_ID)).thenThrow(
                new ApiException(ErrorCode.CONTINUE_POSITION_NOT_FOUND)
        );

        mockMvc.perform(authenticated(get("/api/learning/continue")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("CONTINUE_POSITION_NOT_FOUND"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/learning/continue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getContinuePosition(anyLong());
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder builder
    ) {
        return builder.requestAttr(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(
                        USER_ID,
                        "firebase-uid",
                        "학습자",
                        UserRole.USER
                )
        );
    }
}
