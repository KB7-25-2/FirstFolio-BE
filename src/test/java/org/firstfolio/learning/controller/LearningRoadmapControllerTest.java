package org.firstfolio.learning.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.domain.LearningRoadmapStatus;
import org.firstfolio.learning.dto.response.LearningRoadmapResponse;
import org.firstfolio.learning.service.LearningRoadmapService;
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

class LearningRoadmapControllerTest {

    private static final long USER_ID = 11L;
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 14, 3, 0);

    private LearningRoadmapService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LearningRoadmapService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LearningRoadmapController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsLearningPageRoadmapForCurrentUser() throws Exception {
        when(service.getRoadmap(USER_ID)).thenReturn(
                new LearningRoadmapResponse(List.of(chapter()))
        );

        mockMvc.perform(authenticated(get("/api/learning/roadmap")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].curriculum_item_id")
                        .value(501))
                .andExpect(jsonPath("$.data.items[0].main_chapter_id")
                        .value(8))
                .andExpect(jsonPath("$.data.items[0].chapter_type")
                        .value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[0].status")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items[0].progress_percent")
                        .value(50))
                .andExpect(jsonPath(
                        "$.data.items[0].sub_chapters[0].sub_chapter_id"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.items[0].sub_chapters[0].content_available"
                ).value(true))
                .andExpect(jsonPath(
                        "$.data.items[0].sub_chapters[0].progress_status"
                ).value("IN_PROGRESS"))
                .andExpect(jsonPath(
                        "$.data.items[0].sub_chapters[0].schedule_status"
                ).value("IN_PROGRESS"))
                .andExpect(jsonPath(
                        "$.data.items[0].main_chapter_quiz.question_available"
                ).value(true))
                .andExpect(jsonPath(
                        "$.data.items[0].main_chapter_quiz.available"
                ).value(false))
                .andExpect(jsonPath(
                        "$.data.items[0].main_chapter_quiz.status"
                ).value("LOCKED"));

        verify(service).getRoadmap(USER_ID);
    }

    @Test
    void returnsNotFoundWithoutConfirmedCurriculum() throws Exception {
        when(service.getRoadmap(USER_ID)).thenThrow(
                new ApiException(ErrorCode.CURRICULUM_NOT_FOUND)
        );

        mockMvc.perform(authenticated(get("/api/learning/roadmap")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("CURRICULUM_NOT_FOUND"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/learning/roadmap"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getRoadmap(anyLong());
    }

    private LearningRoadmapResponse.Chapter chapter() {
        LearningRoadmapResponse.SubChapter subChapter =
                new LearningRoadmapResponse.SubChapter(
                        2L,
                        "소단원 1",
                        "소단원 설명",
                        1,
                        2L,
                        true,
                        701L,
                        2L,
                        LearningProgressStatus.IN_PROGRESS,
                        "page-1",
                        NOW.minusDays(1),
                        null,
                        NOW,
                        LearningRoadmapStatus.Schedule.IN_PROGRESS
                );
        LearningRoadmapResponse.MainChapterQuiz quiz =
                new LearningRoadmapResponse.MainChapterQuiz(
                true,
                false,
                LearningRoadmapStatus.Quiz.LOCKED,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new LearningRoadmapResponse.Chapter(
                501L,
                8L,
                "포트폴리오 기초",
                "기초 설명",
                ChapterType.FOUNDATION,
                1,
                LearningRoadmapStatus.Chapter.IN_PROGRESS,
                null,
                50,
                List.of(subChapter),
                quiz
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
