package org.firstfolio.curriculum.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.service.ChapterMetadataService;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChapterControllerTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(
            900L,
            "firebase-admin",
            "관리자",
            UserRole.ADMIN
    );

    private ChapterMetadataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ChapterMetadataService.class);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminChapterController(service))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(converter)
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    void getsMainChaptersWithOptionalFiltersAndSpecifiedFieldNames()
            throws Exception {
        when(service.getAllMainChapters(ChapterType.ASSET, true))
                .thenReturn(List.of(mainChapter()));

        mockMvc.perform(get("/api/admin/main-chapters")
                        .param("chapter_type", "ASSET")
                        .param("is_active", "true")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].main_chapter_id").value(2))
                .andExpect(jsonPath("$.data.items[0].is_required").value(false))
                .andExpect(jsonPath("$.data.items[0].is_active").value(true))
                .andExpect(jsonPath("$.data.items[0].active").doesNotExist());
    }

    @Test
    void createsMainChapterFromNotionRequestContract() throws Exception {
        when(service.createMainChapter(any(), eq(900L), anyString()))
                .thenReturn(mainChapter());

        mockMvc.perform(post("/api/admin/main-chapters")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chapter_type": "ASSET",
                                  "asset_type": "DEPOSIT_SAVINGS",
                                  "title": "예·적금",
                                  "description": "예금과 적금",
                                  "display_order": 2,
                                  "is_required": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.main_chapter_id").value(2))
                .andExpect(jsonPath("$.data.chapter_type").value("ASSET"))
                .andExpect(jsonPath("$.data.is_active").value(true))
                .andExpect(jsonPath("$.data.display_order").doesNotExist());
    }

    @Test
    void rejectsLegacyActiveFieldOnMainChapterCreate() throws Exception {
        mockMvc.perform(post("/api/admin/main-chapters")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chapter_type": "ASSET",
                                  "asset_type": "BOND",
                                  "title": "채권",
                                  "display_order": 3,
                                  "is_required": false,
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void partiallyPatchesMainChapter() throws Exception {
        MainChapter updated = mainChapter();
        updated.setTitle("예·적금의 이해");
        when(service.patchMainChapter(eq(2L), any(), eq(900L), anyString()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/admin/main-chapters/2")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "예·적금의 이해"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("예·적금의 이해"))
                .andExpect(jsonPath("$.data.is_active").value(true))
                .andExpect(jsonPath("$.data.updated_at")
                        .value("2026-08-06T06:00:00Z"));
    }

    @Test
    void getsSubChapterMetadataList() throws Exception {
        when(service.getAllSubChapters(2L)).thenReturn(List.of(subChapter()));

        mockMvc.perform(get("/api/admin/main-chapters/2/sub-chapters")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.items[0].is_active").value(true))
                .andExpect(jsonPath("$.data.items[0].main_chapter_id")
                        .doesNotExist());
    }

    @Test
    void createsSubChapterAsActiveWithoutRequestField() throws Exception {
        when(service.createSubChapter(
                eq(2L),
                any(),
                eq(900L),
                anyString()
        )).thenReturn(subChapter());

        mockMvc.perform(post("/api/admin/main-chapters/2/sub-chapters")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "예금의 이해",
                                  "description": "기본 개념",
                                  "display_order": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.main_chapter_id").value(2))
                .andExpect(jsonPath("$.data.current_content_version_id")
                        .isEmpty())
                .andExpect(jsonPath("$.data.is_active").value(true));
    }

    @Test
    void partiallyPatchesSubChapter() throws Exception {
        when(service.patchSubChapter(
                eq(101L),
                any(),
                eq(900L),
                anyString()
        )).thenReturn(subChapter());

        mockMvc.perform(patch("/api/admin/sub-chapters/101")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                ADMIN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "is_active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.is_active").value(true));

        verify(service).patchSubChapter(
                eq(101L),
                any(),
                eq(900L),
                anyString()
        );
    }

    private MainChapter mainChapter() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 6, 0);
        MainChapter chapter = MainChapter.create(
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                "예·적금",
                "예금과 적금",
                2,
                false,
                true,
                now
        );
        chapter.setMainChapterId(2L);
        return chapter;
    }

    private SubChapter subChapter() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 6, 0);
        SubChapter chapter = SubChapter.create(
                2L,
                "예금의 이해",
                "기본 개념",
                1,
                true,
                now
        );
        chapter.setSubChapterId(101L);
        return chapter;
    }
}
