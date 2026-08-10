package org.firstfolio.learning.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.PublicSubChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicChapterQueryServiceTest {

    private MainChapterMapper mainChapterMapper;
    private SubChapterMapper subChapterMapper;
    private PublicChapterQueryService service;

    @BeforeEach
    void setUp() {
        mainChapterMapper = mock(MainChapterMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        service = new PublicChapterQueryService(mainChapterMapper, subChapterMapper);
    }

    @Test
    void returnsOnlyActiveMainChaptersInMapperOrder() {
        MainChapter foundation = mainChapter(1L, ChapterType.FOUNDATION, null, true);
        MainChapter deposit = mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                true
        );
        when(mainChapterMapper.findAll(null, true))
                .thenReturn(List.of(foundation, deposit));

        List<MainChapter> result = service.getMainChapters();

        assertEquals(List.of(foundation, deposit), result);
        verify(mainChapterMapper).findAll(null, true);
    }

    @Test
    void returnsActiveSubChaptersWithPublishedContentAvailability() {
        MainChapter mainChapter = mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                true
        );
        PublicSubChapter first = publicSubChapter(101L, true);
        PublicSubChapter second = publicSubChapter(102L, false);
        when(mainChapterMapper.findById(2L)).thenReturn(mainChapter);
        when(subChapterMapper.findPublicByMainChapterId(2L))
                .thenReturn(List.of(first, second));

        List<PublicSubChapter> result = service.getSubChapters(2L);

        assertEquals(List.of(first, second), result);
        verify(subChapterMapper).findPublicByMainChapterId(2L);
    }

    @Test
    void hidesMissingOrInactiveMainChapter() {
        when(mainChapterMapper.findById(2L)).thenReturn(null);

        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.getSubChapters(2L)
        );
        assertEquals(ErrorCode.MAIN_CHAPTER_NOT_FOUND, missing.getErrorCode());

        MainChapter inactive = mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                false
        );
        when(mainChapterMapper.findById(2L)).thenReturn(inactive);

        ApiException hidden = assertThrows(
                ApiException.class,
                () -> service.getSubChapters(2L)
        );
        assertEquals(ErrorCode.MAIN_CHAPTER_NOT_FOUND, hidden.getErrorCode());
        verify(subChapterMapper, never()).findPublicByMainChapterId(2L);
    }

    private MainChapter mainChapter(
            long id,
            ChapterType chapterType,
            AssetType assetType,
            boolean active
    ) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setChapterType(chapterType);
        chapter.setAssetType(assetType);
        chapter.setTitle(chapterType == ChapterType.FOUNDATION
                ? "포트폴리오 기초" : "예·적금");
        chapter.setDescription("설명");
        chapter.setDisplayOrder((int) id);
        chapter.setRequired(chapterType == ChapterType.FOUNDATION);
        chapter.setActive(active);
        return chapter;
    }

    private PublicSubChapter publicSubChapter(long id, boolean contentAvailable) {
        PublicSubChapter chapter = new PublicSubChapter();
        chapter.setSubChapterId(id);
        chapter.setMainChapterId(2L);
        chapter.setTitle("소단원 " + id);
        chapter.setDescription("설명");
        chapter.setDisplayOrder((int) (id - 100));
        chapter.setContentAvailable(contentAvailable);
        return chapter;
    }
}
