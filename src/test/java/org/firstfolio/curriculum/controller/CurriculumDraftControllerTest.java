package org.firstfolio.curriculum.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.CurriculumDraftCandidate;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumDraftResult;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.service.CurriculumDraftService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurriculumDraftControllerTest {

    private static final long USER_ID = 11L;

    private CurriculumDraftService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CurriculumDraftService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CurriculumDraftController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsDefaultDraftAndBothCandidateLists() throws Exception {
        when(service.getDefaultDraft(USER_ID)).thenReturn(defaultDraft());

        mockMvc.perform(authenticated(get("/api/curriculum/draft")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].main_chapter_id")
                        .value(1))
                .andExpect(jsonPath("$.data.items[0].source_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[0].display_order")
                        .value(1))
                .andExpect(jsonPath("$.data.items[0].removable")
                        .value(false))
                .andExpect(jsonPath("$.data.items[1].source_type")
                        .value("LEVEL_TEST_WRONG"))
                .andExpect(jsonPath(
                        "$.data.recommendation_candidates[0].main_chapter_id"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.cart_candidates[0].main_chapter_id"
                ).value(3));

        verify(service).getDefaultDraft(USER_ID);
    }

    @Test
    void editsDraftWithoutPersistingAndPreservesRequestedOrder() throws Exception {
        when(service.editDraft(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of(
                item(1L, "포트폴리오 기초",
                        CurriculumSourceType.FOUNDATION, 1, false),
                item(3L, "채권",
                        CurriculumSourceType.USER_ADDED, 2, true),
                item(2L, "예·적금",
                        CurriculumSourceType.LEVEL_TEST_WRONG, 3, true)
        ));

        mockMvc.perform(authenticated(put("/api/curriculum/draft"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "main_chapter_ids": [3, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].source_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[1].main_chapter_id")
                        .value(3))
                .andExpect(jsonPath("$.data.items[1].source_type")
                        .value("USER_ADDED"))
                .andExpect(jsonPath("$.data.items[1].display_order")
                        .value(2))
                .andExpect(jsonPath("$.data.items[2].main_chapter_id")
                        .value(2))
                .andExpect(jsonPath("$.data.items[2].source_type")
                        .value("LEVEL_TEST_WRONG"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).editDraft(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                captor.capture()
        );
        assertEquals(List.of(3L, 2L), captor.getValue());
    }

    @Test
    void supportsFoundationOnlyDraft() throws Exception {
        when(service.editDraft(USER_ID, List.of())).thenReturn(List.of(
                item(1L, "포트폴리오 기초",
                        CurriculumSourceType.FOUNDATION, 1, false)
        ));

        mockMvc.perform(authenticated(put("/api/curriculum/draft"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].source_type")
                        .value("FOUNDATION"));
    }

    @Test
    void returnsLevelTestAndSelectionErrors() throws Exception {
        when(service.getDefaultDraft(USER_ID)).thenThrow(
                new ApiException(ErrorCode.LEVEL_TEST_REQUIRED)
        );
        mockMvc.perform(authenticated(get("/api/curriculum/draft")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("LEVEL_TEST_REQUIRED"));

        when(service.editDraft(USER_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_CURRICULUM_SELECTION)
        );
        mockMvc.perform(authenticated(put("/api/curriculum/draft")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_CURRICULUM_SELECTION"));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/curriculum/draft"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/curriculum/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"main_chapter_ids\":[]}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).getDefaultDraft(anyLong());
        verify(service, never()).editDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private CurriculumDraftResult defaultDraft() {
        return new CurriculumDraftResult(
                List.of(
                        item(1L, "포트폴리오 기초",
                                CurriculumSourceType.FOUNDATION, 1, false),
                        item(2L, "예·적금",
                                CurriculumSourceType.LEVEL_TEST_WRONG, 2, true)
                ),
                List.of(new CurriculumDraftCandidate(2L, "예·적금")),
                List.of(new CurriculumDraftCandidate(3L, "채권"))
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
