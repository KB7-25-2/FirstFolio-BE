package org.firstfolio.learning.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.learning.domain.MainChapterCompletionResult;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.learning.mapper.MainChapterLearningMapper;
import org.firstfolio.portfolio.service.InitialGrantResult;
import org.firstfolio.portfolio.service.InitialGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainChapterCompletionServiceTest {

    private static final long USER_ID = 11L;
    private static final long MAIN_CHAPTER_ID = 10L;
    private static final long CURRICULUM_ITEM_ID = 100L;
    private static final LocalDateTime COMPLETED_AT =
            LocalDateTime.of(2026, 8, 11, 2, 30);

    private MainChapterLearningMapper learningMapper;
    private MainChapterMapper mainChapterMapper;
    private InitialGrantService initialGrantService;
    private MainChapterCompletionService service;

    @BeforeEach
    void setUp() {
        learningMapper = mock(MainChapterLearningMapper.class);
        mainChapterMapper = mock(MainChapterMapper.class);
        initialGrantService = mock(InitialGrantService.class);
        service = new MainChapterCompletionService(
                learningMapper,
                mainChapterMapper,
                initialGrantService
        );
    }

    @Test
    void completesAssetChapterWithoutPassingScoreOrFoundationGrant() {
        arrange(null, ChapterType.ASSET);
        when(learningMapper.completeCurriculumItemIfIncomplete(
                CURRICULUM_ITEM_ID,
                COMPLETED_AT
        )).thenReturn(1);

        MainChapterCompletionResult result = service.complete(
                USER_ID,
                MAIN_CHAPTER_ID,
                COMPLETED_AT
        );

        assertTrue(result.completedNow());
        assertEquals(ChapterType.ASSET, result.chapterType());
        assertNull(result.foundationGrant());
        verify(initialGrantService, never())
                .grantOnFoundationCompleted(USER_ID, CURRICULUM_ITEM_ID);
    }

    @Test
    void preservesFirstCompletionOnReattempt() {
        arrange(COMPLETED_AT.minusDays(1), ChapterType.ASSET);

        MainChapterCompletionResult result = service.complete(
                USER_ID,
                MAIN_CHAPTER_ID,
                COMPLETED_AT
        );

        assertFalse(result.completedNow());
        verify(learningMapper, never()).completeCurriculumItemIfIncomplete(
                CURRICULUM_ITEM_ID,
                COMPLETED_AT
        );
    }

    @Test
    void grantsInitialSimulationMoneyForFoundationChapter() {
        arrange(null, ChapterType.FOUNDATION);
        when(learningMapper.completeCurriculumItemIfIncomplete(
                CURRICULUM_ITEM_ID,
                COMPLETED_AT
        )).thenReturn(1);
        InitialGrantResult grant = new InitialGrantResult(
                true,
                new BigDecimal("30000000.00"),
                501L
        );
        when(initialGrantService.grantOnFoundationCompleted(
                USER_ID,
                CURRICULUM_ITEM_ID
        )).thenReturn(grant);

        MainChapterCompletionResult result = service.complete(
                USER_ID,
                MAIN_CHAPTER_ID,
                COMPLETED_AT
        );

        assertTrue(result.completedNow());
        assertEquals(grant, result.foundationGrant());
    }

    private void arrange(LocalDateTime completedAt, ChapterType type) {
        UserCurriculumItem item = new UserCurriculumItem();
        item.setCurriculumItemId(CURRICULUM_ITEM_ID);
        item.setUserId(USER_ID);
        item.setMainChapterId(MAIN_CHAPTER_ID);
        item.setStatus(CurriculumItemStatus.ACTIVE);
        item.setCompletedAt(completedAt);
        when(learningMapper.findActiveCurriculumItemForUpdate(
                USER_ID,
                MAIN_CHAPTER_ID
        )).thenReturn(item);

        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(MAIN_CHAPTER_ID);
        chapter.setChapterType(type);
        chapter.setActive(true);
        when(mainChapterMapper.findById(MAIN_CHAPTER_ID)).thenReturn(chapter);
    }
}
