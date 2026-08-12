package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumDraftResult;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestChapterGradingResult;
import org.firstfolio.quiz.domain.LevelTestSubmitResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.service.LevelTestSubmitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurriculumDraftServiceTest {

    private static final long USER_ID = 11L;

    private MainChapterMapper mainChapterMapper;
    private LevelTestSubmitService levelTestSubmitService;
    private CurriculumDraftService service;

    @BeforeEach
    void setUp() {
        mainChapterMapper = mock(MainChapterMapper.class);
        levelTestSubmitService = mock(LevelTestSubmitService.class);
        service = new CurriculumDraftService(
                mainChapterMapper,
                levelTestSubmitService
        );
        when(levelTestSubmitService.findResult(USER_ID))
                .thenReturn(levelTestResult());
        when(mainChapterMapper.findAll(ChapterType.FOUNDATION, true))
                .thenReturn(List.of(foundation()));
        when(mainChapterMapper.findAll(ChapterType.ASSET, true))
                .thenReturn(List.of(deposit(), bond()));
    }

    @Test
    void buildsDefaultDraftWithFoundationWrongRecommendationAndCartCandidate() {
        CurriculumDraftResult result = service.getDefaultDraft(USER_ID);

        assertEquals(2, result.items().size());
        assertEquals(1L, result.items().get(0).mainChapterId());
        assertEquals(CurriculumSourceType.FOUNDATION,
                result.items().get(0).sourceType());
        assertEquals(1, result.items().get(0).displayOrder());
        assertFalse(result.items().get(0).removable());
        assertEquals(2L, result.items().get(1).mainChapterId());
        assertEquals(CurriculumSourceType.LEVEL_TEST_WRONG,
                result.items().get(1).sourceType());
        assertEquals(2, result.items().get(1).displayOrder());
        assertTrue(result.items().get(1).removable());

        assertEquals(List.of(2L), result.recommendationCandidates().stream()
                .map(candidate -> candidate.mainChapterId())
                .toList());
        assertEquals(List.of(3L), result.cartCandidates().stream()
                .map(candidate -> candidate.mainChapterId())
                .toList());
    }

    @Test
    void normalizesSelectedChaptersInRequestedOrderAndDerivesSourceType() {
        List<CurriculumDraftItem> items = service.editDraft(
                USER_ID,
                List.of(3L, 2L)
        );

        assertEquals(List.of(1L, 3L, 2L), items.stream()
                .map(CurriculumDraftItem::mainChapterId)
                .toList());
        assertEquals(List.of(
                        CurriculumSourceType.FOUNDATION,
                        CurriculumSourceType.USER_ADDED,
                        CurriculumSourceType.LEVEL_TEST_WRONG
                ),
                items.stream().map(CurriculumDraftItem::sourceType).toList());
        assertEquals(List.of(1, 2, 3), items.stream()
                .map(CurriculumDraftItem::displayOrder)
                .toList());
    }

    @Test
    void allowsFoundationOnlyDraft() {
        List<CurriculumDraftItem> items = service.editDraft(
                USER_ID,
                List.of()
        );

        assertEquals(1, items.size());
        assertEquals(CurriculumSourceType.FOUNDATION,
                items.get(0).sourceType());
        assertFalse(items.get(0).removable());
    }

    @Test
    void rejectsNullDuplicateFoundationAndUnknownSelections() {
        assertInvalidSelection(null);
        assertInvalidSelection(List.of(2L, 2L));
        assertInvalidSelection(List.of(1L));
        assertInvalidSelection(List.of(999L));
    }

    @Test
    void requiresExactlyOneActiveFoundation() {
        when(mainChapterMapper.findAll(ChapterType.FOUNDATION, true))
                .thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getDefaultDraft(USER_ID)
        );

        assertEquals(ErrorCode.CURRICULUM_CONFIGURATION_INVALID,
                exception.getErrorCode());
    }

    @Test
    void propagatesLevelTestRequiredBeforeLoadingChapters() {
        when(levelTestSubmitService.findResult(USER_ID)).thenThrow(
                new ApiException(ErrorCode.LEVEL_TEST_REQUIRED)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getDefaultDraft(USER_ID)
        );

        assertEquals(ErrorCode.LEVEL_TEST_REQUIRED,
                exception.getErrorCode());
        verify(mainChapterMapper, never()).findAll(
                ChapterType.FOUNDATION,
                true
        );
    }

    private void assertInvalidSelection(List<Long> selectedIds) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.editDraft(USER_ID, selectedIds)
        );
        assertEquals(ErrorCode.INVALID_CURRICULUM_SELECTION,
                exception.getErrorCode());
    }

    private LevelTestSubmitResult levelTestResult() {
        return new LevelTestSubmitResult(
                2001L,
                QuizAttemptStatus.GRADED,
                List.of(),
                List.of(
                        new LevelTestChapterGradingResult(
                                2L,
                                AssetType.DEPOSIT_SAVINGS,
                                2,
                                1,
                                false
                        ),
                        new LevelTestChapterGradingResult(
                                3L,
                                AssetType.BOND,
                                1,
                                1,
                                true
                        )
                )
        );
    }

    private MainChapter foundation() {
        MainChapter chapter = chapter(
                1L,
                ChapterType.FOUNDATION,
                null,
                "포트폴리오 기초"
        );
        chapter.setRequired(true);
        return chapter;
    }

    private MainChapter deposit() {
        return chapter(
                2L,
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                "예·적금"
        );
    }

    private MainChapter bond() {
        return chapter(3L, ChapterType.ASSET, AssetType.BOND, "채권");
    }

    private MainChapter chapter(
            long id,
            ChapterType chapterType,
            AssetType assetType,
            String title
    ) {
        MainChapter chapter = new MainChapter();
        chapter.setMainChapterId(id);
        chapter.setChapterType(chapterType);
        chapter.setAssetType(assetType);
        chapter.setTitle(title);
        chapter.setActive(true);
        return chapter;
    }
}
