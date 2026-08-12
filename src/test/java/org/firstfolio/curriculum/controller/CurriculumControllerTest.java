package org.firstfolio.curriculum.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumOverviewItem;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.service.UserCurriculumQueryService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurriculumControllerTest {

    private static final long USER_ID = 11L;

    private UserCurriculumQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(UserCurriculumQueryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CurriculumController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsConfirmedCurriculumInDisplayOrder() throws Exception {
        when(service.findOverview(USER_ID)).thenReturn(List.of(
                item(501L, 1L, "포트폴리오 기초",
                        ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION,
                        1, null, 40),
                item(502L, 2L, "예·적금",
                        ChapterType.ASSET,
                        CurriculumSourceType.LEVEL_TEST_WRONG,
                        2, LocalDateTime.of(2026, 8, 12, 3, 0), 100)
        ));

        mockMvc.perform(authenticated(get("/api/curriculum")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].curriculum_item_id")
                        .value(501))
                .andExpect(jsonPath("$.data.items[0].main_chapter_id")
                        .value(1))
                .andExpect(jsonPath("$.data.items[0].chapter_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[0].display_order")
                        .value(1))
                .andExpect(jsonPath("$.data.items[0].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].completed_at")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.items[0].progress_percent")
                        .value(40))
                .andExpect(jsonPath("$.data.items[1].completed_at")
                        .value("2026-08-12T03:00:00Z"))
                .andExpect(jsonPath("$.data.items[1].progress_percent")
                        .value(100));

        verify(service).findOverview(USER_ID);
    }

    @Test
    void returnsFoundationOnlyCurriculum() throws Exception {
        when(service.findOverview(USER_ID)).thenReturn(List.of(
                item(501L, 1L, "포트폴리오 기초",
                        ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION,
                        1, null, 0)
        ));

        mockMvc.perform(authenticated(get("/api/curriculum")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].progress_percent")
                        .value(0));
    }

    @Test
    void returnsNotFoundForUnconfirmedCurriculum() throws Exception {
        when(service.findOverview(USER_ID)).thenThrow(
                new ApiException(ErrorCode.CURRICULUM_NOT_FOUND)
        );

        mockMvc.perform(authenticated(get("/api/curriculum")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("CURRICULUM_NOT_FOUND"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/curriculum"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).findOverview(anyLong());
    }

    private CurriculumOverviewItem item(
            long curriculumItemId,
            long mainChapterId,
            String title,
            ChapterType chapterType,
            CurriculumSourceType sourceType,
            int displayOrder,
            LocalDateTime completedAt,
            int progressPercent
    ) {
        return new CurriculumOverviewItem(
                curriculumItemId,
                mainChapterId,
                title,
                chapterType,
                displayOrder,
                sourceType,
                CurriculumItemStatus.ACTIVE,
                completedAt,
                progressPercent
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
