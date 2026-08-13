package org.firstfolio.dailyquest.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.dailyquest.domain.DailyQuestAnswerSaveResult;
import org.firstfolio.dailyquest.service.DailyQuestAnswerSaveService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyQuestAnswerControllerTest {

    private static final long USER_ID = 10L;
    private static final long ITEM_ID = 5001L;

    private DailyQuestAnswerSaveService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DailyQuestAnswerSaveService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DailyQuestAnswerController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void savesOneAnswerAndReturnsProgress() throws Exception {
        when(service.save(USER_ID, ITEM_ID, "A")).thenReturn(
                new DailyQuestAnswerSaveResult(
                        4001L,
                        ITEM_ID,
                        "A",
                        2,
                        5
                )
        );

        mockMvc.perform(authenticated(put(
                        "/api/daily-quests/today/items/5001/answer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": {
                                    "key": "A"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily_quest_id").value(4001))
                .andExpect(jsonPath("$.data.daily_quest_item_id")
                        .value(5001))
                .andExpect(jsonPath("$.data.saved_answer.key").value("A"))
                .andExpect(jsonPath("$.data.answered_count").value(2))
                .andExpect(jsonPath("$.data.total_count").value(5))
                .andExpect(jsonPath("$.data.is_correct").doesNotExist())
                .andExpect(jsonPath("$.data.correct_answer").doesNotExist())
                .andExpect(jsonPath("$.data.explanation").doesNotExist());

        verify(service).save(USER_ID, ITEM_ID, "A");
    }

    @Test
    void rejectsPreviousFlatAnswerShape() throws Exception {
        mockMvc.perform(authenticated(put(
                        "/api/daily-quests/today/items/5001/answer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verify(service, never()).save(anyLong(), anyLong(), any());
    }

    @Test
    void returnsInvalidAnswerForMissingKey() throws Exception {
        when(service.save(USER_ID, ITEM_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_ANSWER)
        );

        mockMvc.perform(authenticated(put(
                        "/api/daily-quests/today/items/5001/answer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_ANSWER"));
    }

    @Test
    void returnsInvalidAnswerForMissingBody() throws Exception {
        when(service.save(USER_ID, ITEM_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_ANSWER)
        );

        mockMvc.perform(authenticated(put(
                "/api/daily-quests/today/items/5001/answer")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_ANSWER"));
    }

    @Test
    void returnsItemNotFound() throws Exception {
        when(service.save(USER_ID, ITEM_ID, "A")).thenThrow(
                new ApiException(ErrorCode.DAILY_QUEST_ITEM_NOT_FOUND)
        );

        mockMvc.perform(authenticated(request("A")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("DAILY_QUEST_ITEM_NOT_FOUND"));
    }

    @Test
    void returnsAlreadyCompletedConflict() throws Exception {
        when(service.save(USER_ID, ITEM_ID, "A")).thenThrow(
                new ApiException(ErrorCode.DAILY_QUEST_ALREADY_COMPLETED)
        );

        mockMvc.perform(authenticated(request("A")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("DAILY_QUEST_ALREADY_COMPLETED"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(request("A"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).save(anyLong(), anyLong(), any());
    }

    private MockHttpServletRequestBuilder request(String key) {
        return put("/api/daily-quests/today/items/5001/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":{\"key\":\"" + key + "\"}}");
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
