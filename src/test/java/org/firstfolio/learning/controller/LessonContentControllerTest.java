package org.firstfolio.learning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LessonContent;
import org.firstfolio.learning.service.LessonContentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LessonContentControllerTest {

    private LessonContentQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LessonContentQueryService.class);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LessonContentController(service))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(converter)
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    void returnsPublishedLessonJsonWithoutStorageReference() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        when(service.getPublishedLesson(103L)).thenReturn(new LessonContent(
                103L,
                "예금의 기초",
                302L,
                "1.0",
                objectMapper.readTree("""
                        {
                          "schemaVersion": "1.0",
                          "pages": [{"id": "interest", "blocks": []}],
                          "subChapterQuiz": {"questionIds": [1021]}
                        }
                        """)
        ));

        mockMvc.perform(get("/api/learning/sub-chapters/103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sub_chapter_id").value(103))
                .andExpect(jsonPath("$.data.title").value("예금의 기초"))
                .andExpect(jsonPath("$.data.content_version_id").value(302))
                .andExpect(jsonPath("$.data.schema_version").value("1.0"))
                .andExpect(jsonPath("$.data.lesson.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.data.lesson.pages[0].id").value("interest"))
                .andExpect(jsonPath("$.data.storage_object_key").doesNotExist())
                .andExpect(jsonPath("$.data.storage_version_id").doesNotExist());

        verify(service).getPublishedLesson(103L);
    }

    @Test
    void returnsNotFoundWhenContentIsNotPublished() throws Exception {
        when(service.getPublishedLesson(103L))
                .thenThrow(new ApiException(ErrorCode.CONTENT_NOT_PUBLISHED));

        mockMvc.perform(get("/api/learning/sub-chapters/103"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_PUBLISHED"));
    }

    @Test
    void returnsServiceUnavailableWhenStorageCannotLoadContent() throws Exception {
        when(service.getPublishedLesson(103L))
                .thenThrow(new ApiException(ErrorCode.CONTENT_UNAVAILABLE));

        mockMvc.perform(get("/api/learning/sub-chapters/103"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CONTENT_UNAVAILABLE"));
    }
}
