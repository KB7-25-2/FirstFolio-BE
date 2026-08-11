package org.firstfolio.learning.controller;

import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.PublicSubChapter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.service.PublicChapterQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicChapterControllerTest {

    private PublicChapterQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PublicChapterQueryService.class);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicChapterController(service))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(converter)
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    void returnsActiveMainChapterMetadataWithoutInternalFields() throws Exception {
        MainChapter foundation = mainChapter(
                1L,
                ChapterType.FOUNDATION,
                null,
                "포트폴리오 기초",
                true
        );
        when(service.getMainChapters()).thenReturn(List.of(foundation));

        mockMvc.perform(get("/api/learning/main-chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].main_chapter_id").value(1))
                .andExpect(jsonPath("$.data.items[0].chapter_type").value("FOUNDATION"))
                .andExpect(jsonPath("$.data.items[0].asset_type").isEmpty())
                .andExpect(jsonPath("$.data.items[0].title").value("포트폴리오 기초"))
                .andExpect(jsonPath("$.data.items[0].display_order").value(1))
                .andExpect(jsonPath("$.data.items[0].is_required").value(true))
                .andExpect(jsonPath("$.data.items[0].is_active").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].created_at").doesNotExist());

        verify(service).getMainChapters();
    }

    @Test
    void returnsSubChaptersWithContentAvailabilityWithoutStorageReference()
            throws Exception {
        PublicSubChapter published = publicSubChapter(101L, 1, true);
        PublicSubChapter unpublished = publicSubChapter(102L, 2, false);
        when(service.getSubChapters(2L))
                .thenReturn(List.of(published, unpublished));

        mockMvc.perform(get("/api/learning/main-chapters/2/sub-chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.items[0].display_order").value(1))
                .andExpect(jsonPath("$.data.items[0].content_available").value(true))
                .andExpect(jsonPath("$.data.items[1].sub_chapter_id").value(102))
                .andExpect(jsonPath("$.data.items[1].content_available").value(false))
                .andExpect(jsonPath("$.data.items[0].current_content_version_id")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.items[0].storage_object_key").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].storage_version_id").doesNotExist());

        verify(service).getSubChapters(2L);
    }

    @Test
    void returnsNotFoundForMissingOrInactiveMainChapter() throws Exception {
        when(service.getSubChapters(2L))
                .thenThrow(new ApiException(ErrorCode.MAIN_CHAPTER_NOT_FOUND));

        mockMvc.perform(get("/api/learning/main-chapters/2/sub-chapters"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MAIN_CHAPTER_NOT_FOUND"));
    }

    private MainChapter mainChapter(
            long id,
            ChapterType chapterType,
            AssetType assetType,
            String title,
            boolean required
    ) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setChapterType(chapterType);
        chapter.setAssetType(assetType);
        chapter.setTitle(title);
        chapter.setDescription("설명");
        chapter.setDisplayOrder((int) id);
        chapter.setRequired(required);
        chapter.setActive(true);
        return chapter;
    }

    private PublicSubChapter publicSubChapter(
            long id,
            int displayOrder,
            boolean contentAvailable
    ) {
        PublicSubChapter chapter = new PublicSubChapter();
        chapter.setSubChapterId(id);
        chapter.setMainChapterId(2L);
        chapter.setTitle("소단원 " + id);
        chapter.setDescription("설명");
        chapter.setDisplayOrder(displayOrder);
        chapter.setContentAvailable(contentAvailable);
        return chapter;
    }
}
