package org.firstfolio.curriculum.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.service.CurriculumConfirmService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurriculumConfirmControllerTest {

    private static final long USER_ID = 11L;

    private CurriculumConfirmService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CurriculumConfirmService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CurriculumConfirmController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void confirmsCurriculumAndReturnsNormalizedItems() throws Exception {
        when(service.confirm(eq(USER_ID), any())).thenReturn(items());

        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[3,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].main_chapter_id")
                        .value(1))
                .andExpect(jsonPath("$.data.items[0].source_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[0].display_order")
                        .value(1))
                .andExpect(jsonPath("$.data.items[0].removable")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.items[1].main_chapter_id")
                        .value(3))
                .andExpect(jsonPath("$.data.items[1].source_type")
                        .value("USER_ADDED"))
                .andExpect(jsonPath("$.data.items[2].source_type")
                        .value("LEVEL_TEST_WRONG"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).confirm(eq(USER_ID), captor.capture());
        assertEquals(List.of(3L, 2L), captor.getValue());
    }

    @Test
    void confirmsFoundationOnlyCurriculum() throws Exception {
        when(service.confirm(USER_ID, List.of())).thenReturn(
                List.of(items().get(0))
        );

        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].source_type")
                        .value("FOUNDATION"));
    }

    @Test
    void returnsConfirmationErrors() throws Exception {
        when(service.confirm(USER_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_CURRICULUM_SELECTION)
        );
        mockMvc.perform(authenticated(post("/api/curriculum/confirm")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_CURRICULUM_SELECTION"));

        when(service.confirm(USER_ID, List.of(2L))).thenThrow(
                new ApiException(ErrorCode.CURRICULUM_ALREADY_CONFIRMED)
        );
        mockMvc.perform(authenticated(post("/api/curriculum/confirm"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[2]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("CURRICULUM_ALREADY_CONFIRMED"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/curriculum/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[]}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).confirm(anyLong(), any());
    }

    private List<CurriculumDraftItem> items() {
        return List.of(
                item(1L, "포트폴리오 기초",
                        CurriculumSourceType.FOUNDATION, 1, false),
                item(3L, "채권",
                        CurriculumSourceType.USER_ADDED, 2, true),
                item(2L, "예·적금",
                        CurriculumSourceType.LEVEL_TEST_WRONG, 3, true)
        );
    }

    private CurriculumDraftItem item(
            long mainChapterId,
            String title,
            CurriculumSourceType sourceType,
            int displayOrder,
            boolean removable
    ) {
        return new CurriculumDraftItem(
                mainChapterId,
                title,
                sourceType,
                displayOrder,
                removable
        );
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request
    ) {
        return request.requestAttr(
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
