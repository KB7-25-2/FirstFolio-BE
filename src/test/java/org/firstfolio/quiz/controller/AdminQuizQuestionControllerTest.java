package org.firstfolio.quiz.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.service.QuizQuestionRegistrationService;
import org.firstfolio.quiz.service.QuizQuestionPublicationService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminQuizQuestionControllerTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(
            900L,
            "firebase-admin",
            "관리자",
            UserRole.ADMIN
    );

    private QuizQuestionRegistrationService service;
    private QuizQuestionPublicationService publicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(QuizQuestionRegistrationService.class);
        publicationService = mock(QuizQuestionPublicationService.class);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminQuizQuestionController(
                        service,
                        publicationService
                ))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(converter)
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    void createsFirstDraftUsingQuizJsonSchemaFieldNames() throws Exception {
        when(service.createQuestion(any(), eq(900L), anyString()))
                .thenReturn(question(1201L, 1));

        mockMvc.perform(post("/api/admin/quiz-questions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.question_id").value(1201))
                .andExpect(jsonPath("$.data.question_key").value("deposit-basic-001"))
                .andExpect(jsonPath("$.data.version_no").value(1))
                .andExpect(jsonPath("$.data.display_order").value(1))
                .andExpect(jsonPath("$.data.generation_type").value("HUMAN"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(service).createQuestion(any(), eq(900L), anyString());
    }

    @Test
    void createsNewVersionFromExistingQuestionId() throws Exception {
        when(service.createVersion(eq(1201L), any(), eq(900L), anyString()))
                .thenReturn(question(1202L, 2));

        mockMvc.perform(post("/api/admin/quiz-questions/1201/versions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.question_id").value(1202))
                .andExpect(jsonPath("$.data.question_key").value("deposit-basic-001"))
                .andExpect(jsonPath("$.data.version_no").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(service).createVersion(eq(1201L), any(), eq(900L), anyString());
    }

    @Test
    void bindsCanonicalJsonFieldsToCreateRequest() throws Exception {
        when(service.createQuestion(any(), eq(900L), anyString()))
                .thenReturn(question(1201L, 1));
        ArgumentCaptor<org.firstfolio.quiz.dto.request.QuizQuestionCreateRequest> captor =
                ArgumentCaptor.forClass(
                        org.firstfolio.quiz.dto.request.QuizQuestionCreateRequest.class
                );

        mockMvc.perform(post("/api/admin/quiz-questions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated());

        verify(service).createQuestion(captor.capture(), eq(900L), anyString());
        assertEquals("SUB_CHAPTER", captor.getValue().usageType());
        assertEquals(1, captor.getValue().displayOrder());
        assertEquals("SINGLE_CHOICE", captor.getValue().questionType());
        assertEquals("1", captor.getValue().correctAnswerJson().path("key").textValue());
        assertEquals(2, captor.getValue().optionsJson().size());
    }

    @Test
    void rejectsOutdatedNotionBodyAliasesAsUnknownFields() throws Exception {
        mockMvc.perform(post("/api/admin/quiz-questions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question_key": "deposit-basic-001",
                                  "usage_type": "SUB_CHAPTER",
                                  "question_type": "SINGLE_CHOICE",
                                  "prompt": "질문",
                                  "choices": [],
                                  "correct_answer": {"key": "1"},
                                  "explanation": "해설"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsNotionSpecifiedQuestionErrors() throws Exception {
        when(service.createQuestion(any(), eq(900L), anyString()))
                .thenThrow(new ApiException(ErrorCode.QUESTION_KEY_CONFLICT));
        when(service.createVersion(eq(9999L), any(), eq(900L), anyString()))
                .thenThrow(new ApiException(ErrorCode.QUESTION_NOT_FOUND));

        mockMvc.perform(post("/api/admin/quiz-questions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("QUESTION_KEY_CONFLICT"));

        mockMvc.perform(post("/api/admin/quiz-questions/9999/versions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void publishesLatestDraftQuestionVersion() throws Exception {
        QuizQuestion published = question(1202L, 2);
        published.publish(LocalDateTime.of(2026, 8, 14, 6, 0));
        when(publicationService.publish(eq(1202L), eq(900L), anyString()))
                .thenReturn(published);

        mockMvc.perform(post("/api/admin/quiz-questions/1202/publish")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question_id").value(1202))
                .andExpect(jsonPath("$.data.question_key").value("deposit-basic-001"))
                .andExpect(jsonPath("$.data.version_no").value(2))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.published_at")
                        .value("2026-08-14T06:00:00Z"));

        verify(publicationService).publish(eq(1202L), eq(900L), anyString());
    }

    @Test
    void retiresPublishedQuestionVersion() throws Exception {
        QuizQuestion retired = question(1201L, 1);
        retired.publish(LocalDateTime.of(2026, 8, 13, 6, 0));
        retired.retire();
        when(publicationService.retire(eq(1201L), eq(900L), anyString()))
                .thenReturn(retired);

        mockMvc.perform(post("/api/admin/quiz-questions/1201/retire")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question_id").value(1201))
                .andExpect(jsonPath("$.data.status").value("RETIRED"))
                .andExpect(jsonPath("$.data.published_at")
                        .value("2026-08-13T06:00:00Z"));

        verify(publicationService).retire(eq(1201L), eq(900L), anyString());
    }

    @Test
    void returnsQuestionStateTransitionErrors() throws Exception {
        when(publicationService.publish(eq(1201L), eq(900L), anyString()))
                .thenThrow(new ApiException(ErrorCode.QUESTION_NOT_PUBLISHABLE));
        when(publicationService.retire(eq(1202L), eq(900L), anyString()))
                .thenThrow(new ApiException(ErrorCode.QUESTION_NOT_RETIRABLE));

        mockMvc.perform(post("/api/admin/quiz-questions/1201/publish")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("QUESTION_NOT_PUBLISHABLE"));

        mockMvc.perform(post("/api/admin/quiz-questions/1202/retire")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("QUESTION_NOT_RETIRABLE"));
    }

    private QuizQuestion question(long id, int versionNo) {
        QuizQuestion question = QuizQuestion.draft(
                "deposit-basic-001",
                versionNo,
                QuizUsageType.SUB_CHAPTER,
                2L,
                101L,
                1,
                QuizQuestionType.SINGLE_CHOICE,
                QuizDifficulty.EASY,
                "질문",
                null,
                "[]",
                "{\"key\":\"1\"}",
                "해설",
                QuizGenerationType.HUMAN,
                null,
                null,
                900L,
                LocalDateTime.of(2026, 8, 10, 6, 0)
        );
        question.setQuestionId(id);
        return question;
    }

    private String createRequestBody() {
        return """
                {
                  "question_key": "deposit-basic-001",
                  "usage_type": "SUB_CHAPTER",
                  "main_chapter_id": null,
                  "sub_chapter_id": 101,
                  "display_order": 1,
                  "question_type": "SINGLE_CHOICE",
                  "difficulty": "EASY",
                  "prompt": "예금의 특징으로 적절한 것은?",
                  "scenario_json": null,
                  "options_json": [
                    {"key": "1", "label": "선택지 1"},
                    {"key": "2", "label": "선택지 2", "description": null}
                  ],
                  "correct_answer_json": {"key": "1"},
                  "explanation": "정답 해설"
                }
                """;
    }

    private String versionRequestBody() {
        return """
                {
                  "prompt": "수정된 질문",
                  "scenario_json": null,
                  "options_json": [
                    {"key": "1", "label": "선택지 1"},
                    {"key": "2", "label": "선택지 2"}
                  ],
                  "correct_answer_json": {"key": "1"},
                  "explanation": "수정된 해설",
                  "source_refs_json": null
                }
                """;
    }
}
