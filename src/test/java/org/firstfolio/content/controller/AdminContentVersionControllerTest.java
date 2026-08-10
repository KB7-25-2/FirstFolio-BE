package org.firstfolio.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionHistory;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.dto.request.LessonContentUploadRequest;
import org.firstfolio.content.service.ContentVersionService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminContentVersionControllerTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(
            900L,
            "firebase-admin",
            "관리자",
            UserRole.ADMIN
    );

    private ContentVersionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContentVersionService.class);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminContentVersionController(service))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(converter)
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    void uploadsLessonAndReturnsDraftMetadata() throws Exception {
        when(service.uploadLesson(eq(103L), any(), eq(900L), anyString()))
                .thenReturn(contentVersion());

        mockMvc.perform(post("/api/admin/sub-chapters/103/content-versions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content_version_id").value(302))
                .andExpect(jsonPath("$.data.sub_chapter_id").value(103))
                .andExpect(jsonPath("$.data.version_no").value(2))
                .andExpect(jsonPath("$.data.schema_version").value("1.0"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.validated").value(true))
                .andExpect(jsonPath("$.data.storage_object_key").doesNotExist())
                .andExpect(jsonPath("$.data.storage_version_id").doesNotExist());

        ArgumentCaptor<LessonContentUploadRequest> request =
                ArgumentCaptor.forClass(LessonContentUploadRequest.class);
        verify(service).uploadLesson(eq(103L), request.capture(), eq(900L), anyString());
        assertEquals(2, request.getValue().versionNo());
        assertEquals("1.0", request.getValue().lesson().path("schemaVersion").textValue());
        assertTrue(request.getValue().lesson().path("pages").isArray());
    }

    @Test
    void rejectsLegacyStorageReferenceRequestFields() throws Exception {
        mockMvc.perform(post("/api/admin/sub-chapters/103/content-versions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version_no": 2,
                                  "schema_version": "1.0",
                                  "s3_object_key": "learning/old.json",
                                  "s3_version_id": "old-version"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsContentValidationFailureFromService() throws Exception {
        when(service.uploadLesson(eq(103L), any(), eq(900L), anyString()))
                .thenThrow(new ApiException(ErrorCode.CONTENT_VALIDATION_FAILED));

        mockMvc.perform(post("/api/admin/sub-chapters/103/content-versions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("CONTENT_VALIDATION_FAILED"));
    }

    @Test
    void returnsContentVersionsAndMarksCurrentVersion() throws Exception {
        ContentVersion current = publishedContentVersion();
        ContentVersion draft = contentVersion();
        when(service.getContentVersions(103L)).thenReturn(new ContentVersionHistory(
                List.of(draft, current),
                301L
        ));

        mockMvc.perform(get("/api/admin/sub-chapters/103/content-versions")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content_version_id").value(302))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[0].current").value(false))
                .andExpect(jsonPath("$.data.items[1].content_version_id").value(301))
                .andExpect(jsonPath("$.data.items[1].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.items[1].current").value(true));

        verify(service).getContentVersions(103L);
    }

    @Test
    void publishesDraftContentVersion() throws Exception {
        ContentVersion published = contentVersion();
        published.publish(LocalDateTime.of(2026, 8, 7, 2, 0));
        when(service.publishContentVersion(eq(302L), eq(900L), anyString()))
                .thenReturn(published);

        mockMvc.perform(post("/api/admin/content-versions/302/publish")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content_version_id").value(302))
                .andExpect(jsonPath("$.data.sub_chapter_id").value(103))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.published_at").value("2026-08-07T02:00:00Z"))
                .andExpect(jsonPath("$.data.current").value(true));

        verify(service).publishContentVersion(eq(302L), eq(900L), anyString());
    }

    @Test
    void returnsConflictForNonPublishableContentVersion() throws Exception {
        when(service.publishContentVersion(eq(302L), eq(900L), anyString()))
                .thenThrow(new ApiException(ErrorCode.CONTENT_NOT_PUBLISHABLE));

        mockMvc.perform(post("/api/admin/content-versions/302/publish")
                        .requestAttr(AuthenticationRequestAttributes.CURRENT_USER, ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("CONTENT_NOT_PUBLISHABLE"));
    }

    private ContentVersion contentVersion() {
        ContentVersion version = ContentVersion.draft(
                103L,
                2,
                "1.0",
                new StoredObjectRef(
                        "learning/sub-chapters/103/lesson.json",
                        "storage-version-1"
                ),
                900L,
                LocalDateTime.of(2026, 8, 7, 1, 0)
        );
        version.setContentVersionId(302L);
        return version;
    }

    private ContentVersion publishedContentVersion() {
        ContentVersion version = ContentVersion.draft(
                103L,
                1,
                "1.0",
                new StoredObjectRef(
                        "learning/sub-chapters/103/lesson.json",
                        "storage-version-0"
                ),
                900L,
                LocalDateTime.of(2026, 8, 6, 1, 0)
        );
        version.setContentVersionId(301L);
        version.publish(LocalDateTime.of(2026, 8, 6, 2, 0));
        return version;
    }

    private String requestBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(mapper.readTree("""
                {
                  "version_no": 2,
                  "lesson": {
                    "schemaVersion": "1.0",
                    "pages": [
                      {
                        "id": "interest-rate",
                        "title": "금리",
                        "blocks": [
                          {"type": "text", "content": "금리를 알아봅니다."}
                        ]
                      }
                    ],
                    "subChapterQuiz": {"questionIds": [1021]}
                  }
                }
                """));
    }
}
