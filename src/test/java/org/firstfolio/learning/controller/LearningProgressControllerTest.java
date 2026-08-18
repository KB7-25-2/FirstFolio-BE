package org.firstfolio.learning.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.domain.LearningProgressStatusResult;
import org.firstfolio.learning.domain.LearningProgressUpdateCommand;
import org.firstfolio.learning.domain.LearningProgressUpdateResult;
import org.firstfolio.learning.domain.SubChapterQuizProgress;
import org.firstfolio.learning.service.LearningProgressService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningProgressControllerTest {

    private static final long USER_ID = 11L;
    private static final long SUB_CHAPTER_ID = 101L;

    private LearningProgressService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LearningProgressService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LearningProgressController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void putCreatesOrUpdatesLearningProgress() throws Exception {
        LearningProgress progress = progress(LearningProgressStatus.IN_PROGRESS);
        when(service.save(eq(USER_ID), eq(SUB_CHAPTER_ID), any()))
                .thenReturn(new LearningProgressUpdateResult(progress, true));

        mockMvc.perform(authenticated(put(
                        "/api/learning/sub-chapters/101/progress"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content_version_id": 301,
                                  "last_page_id": "page-2",
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.content_version_id").value(301))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.started_at")
                        .value("2026-08-10T01:30:00Z"))
                .andExpect(jsonPath("$.data.last_page_id").value("page-2"))
                .andExpect(jsonPath("$.data.updated").value(true));

        ArgumentCaptor<LearningProgressUpdateCommand> captor =
                ArgumentCaptor.forClass(LearningProgressUpdateCommand.class);
        verify(service).save(eq(USER_ID), eq(SUB_CHAPTER_ID), captor.capture());
        assertEquals(301L, captor.getValue().contentVersionId());
        assertEquals("page-2", captor.getValue().lastPageId());
        assertEquals(LearningProgressStatus.IN_PROGRESS, captor.getValue().status());
    }

    @Test
    void postStartEndpointNoLongerExists() throws Exception {
        mockMvc.perform(authenticated(post(
                        "/api/learning/sub-chapters/101/progress")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void returnsNotStartedStatusWithoutCreatingProgress() throws Exception {
        LearningProgress progress = progress(LearningProgressStatus.NOT_STARTED);
        progress.setStartedAt(null);
        SubChapterQuizProgress quizProgress = new SubChapterQuizProgress();
        quizProgress.setActiveAttemptId(3001L);
        quizProgress.setAnsweredCount(1);
        quizProgress.setTotalCount(3);
        when(service.getStatus(USER_ID, SUB_CHAPTER_ID)).thenReturn(
                new LearningProgressStatusResult(progress, quizProgress)
        );

        mockMvc.perform(authenticated(get(
                        "/api/learning/sub-chapters/101/progress")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.content_version_id").value(301))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.started_at").isEmpty())
                .andExpect(jsonPath("$.data.completed_at").isEmpty())
                .andExpect(jsonPath("$.data.quiz.completed").value(false))
                .andExpect(jsonPath("$.data.quiz.active_attempt_id").value(3001))
                .andExpect(jsonPath("$.data.quiz.answered_count").value(1))
                .andExpect(jsonPath("$.data.quiz.total_count").value(3));

        verify(service).getStatus(USER_ID, SUB_CHAPTER_ID);
    }

    @Test
    void rejectsInvalidPutStatus() throws Exception {
        mockMvc.perform(authenticated(put(
                        "/api/learning/sub-chapters/101/progress"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content_version_id": 301,
                                  "last_page_id": null,
                                  "status": "NOT_STARTED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verify(service, never()).save(anyLong(), anyLong(), any());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/learning/sub-chapters/101/progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getStatus(anyLong(), anyLong());
    }

    @Test
    void returnsContentVersionMismatch() throws Exception {
        when(service.save(eq(USER_ID), eq(SUB_CHAPTER_ID), any()))
                .thenThrow(new ApiException(ErrorCode.CONTENT_VERSION_MISMATCH));

        mockMvc.perform(authenticated(put(
                        "/api/learning/sub-chapters/101/progress"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content_version_id": 999,
                                  "last_page_id": "page-2",
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("CONTENT_VERSION_MISMATCH"));
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder builder
    ) {
        return builder.requestAttr(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(USER_ID, "firebase-uid", "학습자", UserRole.USER)
        );
    }

    private LearningProgress progress(LearningProgressStatus status) {
        LearningProgress progress = new LearningProgress();
        progress.setProgressId(901L);
        progress.setUserId(USER_ID);
        progress.setSubChapterId(SUB_CHAPTER_ID);
        progress.setContentVersionId(301L);
        progress.setLastPageId("page-2");
        progress.setStatus(status);
        progress.setStartedAt(LocalDateTime.of(2026, 8, 10, 1, 30));
        progress.setUpdatedAt(LocalDateTime.of(2026, 8, 10, 1, 30));
        return progress;
    }
}
