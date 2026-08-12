package org.firstfolio.quiz.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveCommand;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.service.LevelTestAnswerSaveService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LevelTestAnswerControllerTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 2001L;

    private LevelTestAnswerSaveService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LevelTestAnswerSaveService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LevelTestAnswerController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void savesBatchAnswersWithoutReturningGradingInformation() throws Exception {
        when(service.save(anyLong(), anyLong(), any())).thenReturn(
                new LevelTestAnswerSaveResult(
                        ATTEMPT_ID,
                        2,
                        5,
                        8,
                        QuizAttemptStatus.IN_PROGRESS,
                        LocalDateTime.of(2026, 8, 12, 2, 0)
                )
        );

        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/2001/answers"
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {
                                      "question_id": 1001,
                                      "answer": {"key": "B"}
                                    },
                                    {
                                      "question_id": 1002,
                                      "answer": {"key": "A"}
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt_id").value(2001))
                .andExpect(jsonPath("$.data.saved_answer_count").value(2))
                .andExpect(jsonPath("$.data.answered_count").value(5))
                .andExpect(jsonPath("$.data.total_count").value(8))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.updated_at")
                        .value("2026-08-12T02:00:00Z"))
                .andExpect(jsonPath("$.data.is_correct").doesNotExist())
                .andExpect(jsonPath("$.data.correct_answer").doesNotExist())
                .andExpect(jsonPath("$.data.explanation").doesNotExist());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LevelTestAnswerSaveCommand>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(service).save(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(ATTEMPT_ID),
                captor.capture()
        );
        assertEquals(List.of(1001L, 1002L), captor.getValue().stream()
                .map(LevelTestAnswerSaveCommand::questionId)
                .toList());
        assertEquals(List.of("B", "A"), captor.getValue().stream()
                .map(LevelTestAnswerSaveCommand::selectedKey)
                .toList());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(put("/api/level-tests/attempts/2001/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).save(anyLong(), anyLong(), any());
    }

    @Test
    void returnsInvalidRequestForMissingBody() throws Exception {
        when(service.save(USER_ID, ATTEMPT_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_REQUEST)
        );

        mockMvc.perform(authenticated(put(
                "/api/level-tests/attempts/2001/answers"
        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsAttemptAndChoiceErrors() throws Exception {
        doThrow(new ApiException(ErrorCode.ATTEMPT_ALREADY_GRADED))
                .when(service).save(anyLong(), anyLong(), any());

        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/2001/answers"
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("ATTEMPT_ALREADY_GRADED"));

        doThrow(new ApiException(ErrorCode.INVALID_SELECTED_CHOICE))
                .when(service).save(anyLong(), anyLong(), any());

        mockMvc.perform(authenticated(put(
                        "/api/level-tests/attempts/2001/answers"
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_SELECTED_CHOICE"));
    }

    private String validBody() {
        return """
                {
                  "answers": [
                    {
                      "question_id": 1001,
                      "answer": {"key": "B"}
                    }
                  ]
                }
                """;
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
