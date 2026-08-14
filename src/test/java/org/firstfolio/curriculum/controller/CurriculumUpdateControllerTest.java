package org.firstfolio.curriculum.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.service.CurriculumUpdateService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurriculumUpdateControllerTest {

    private static final long USER_ID = 11L;

    private CurriculumUpdateService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CurriculumUpdateService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CurriculumUpdateController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void updatesCurriculumAndReturnsNormalizedItems() throws Exception {
        when(service.update(eq(USER_ID), any())).thenReturn(items());

        mockMvc.perform(authenticated(put("/api/curriculum"))
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
                .andExpect(jsonPath("$.data.items[1].main_chapter_id")
                        .value(3))
                .andExpect(jsonPath("$.data.items[2].main_chapter_id")
                        .value(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).update(eq(USER_ID), captor.capture());
        assertEquals(List.of(3L, 2L), captor.getValue());
    }

    @Test
    void returnsUpdateErrors() throws Exception {
        when(service.update(USER_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_CURRICULUM_SELECTION)
        );
        mockMvc.perform(authenticated(put("/api/curriculum")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_CURRICULUM_SELECTION"));

        when(service.update(USER_ID, List.of(2L))).thenThrow(
                new ApiException(ErrorCode.CURRICULUM_NOT_FOUND)
        );
        mockMvc.perform(authenticated(put("/api/curriculum"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[2]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("CURRICULUM_NOT_FOUND"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(put("/api/curriculum")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[]}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).update(anyLong(), any());
    }

    private List<CurriculumDraftItem> items() {
        return List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1),
                item(3L, CurriculumSourceType.USER_ADDED, 2),
                item(2L, CurriculumSourceType.LEVEL_TEST_WRONG, 3)
        );
    }

    private CurriculumDraftItem item(
            long mainChapterId,
            CurriculumSourceType sourceType,
            int displayOrder
    ) {
        return new CurriculumDraftItem(
                mainChapterId,
                "대단원 " + mainChapterId,
                sourceType,
                displayOrder,
                sourceType != CurriculumSourceType.FOUNDATION
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
