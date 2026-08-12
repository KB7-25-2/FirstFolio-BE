package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumDraftCandidate;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumDraftResult;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestChapterGradingResult;
import org.firstfolio.quiz.domain.LevelTestSubmitResult;
import org.firstfolio.quiz.service.LevelTestSubmitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CurriculumDraftService {

    private final MainChapterMapper mainChapterMapper;
    private final LevelTestSubmitService levelTestSubmitService;

    public CurriculumDraftService(
            MainChapterMapper mainChapterMapper,
            LevelTestSubmitService levelTestSubmitService
    ) {
        this.mainChapterMapper = mainChapterMapper;
        this.levelTestSubmitService = levelTestSubmitService;
    }

    @Transactional(readOnly = true)
    public CurriculumDraftResult getDefaultDraft(long userId) {
        CurriculumContext context = loadContext(userId);
        List<CurriculumDraftItem> items = new ArrayList<>();
        items.add(foundationItem(context.foundation()));

        List<CurriculumDraftCandidate> recommendations = new ArrayList<>();
        List<CurriculumDraftCandidate> cartCandidates = new ArrayList<>();
        for (LevelTestChapterGradingResult result : context.chapterResults()) {
            MainChapter chapter = context.activeAssets().get(
                    result.mainChapterId()
            );
            if (chapter == null) {
                continue;
            }
            CurriculumDraftCandidate candidate = candidate(chapter);
            if (result.allCorrect()) {
                cartCandidates.add(candidate);
            } else {
                recommendations.add(candidate);
                items.add(item(
                        chapter,
                        CurriculumSourceType.LEVEL_TEST_WRONG,
                        items.size() + 1,
                        true
                ));
            }
        }

        return new CurriculumDraftResult(
                items,
                recommendations,
                cartCandidates
        );
    }

    @Transactional(readOnly = true)
    public List<CurriculumDraftItem> editDraft(
            long userId,
            List<Long> mainChapterIds
    ) {
        CurriculumContext context = loadContext(userId);
        List<Long> selectedIds = validateSelection(
                mainChapterIds,
                context
        );
        Set<Long> wrongChapterIds = context.chapterResults().stream()
                .filter(result -> !result.allCorrect())
                .map(LevelTestChapterGradingResult::mainChapterId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<CurriculumDraftItem> items = new ArrayList<>();
        items.add(foundationItem(context.foundation()));
        for (Long mainChapterId : selectedIds) {
            MainChapter chapter = context.activeAssets().get(mainChapterId);
            CurriculumSourceType sourceType = wrongChapterIds.contains(
                    mainChapterId
            )
                    ? CurriculumSourceType.LEVEL_TEST_WRONG
                    : CurriculumSourceType.USER_ADDED;
            items.add(item(
                    chapter,
                    sourceType,
                    items.size() + 1,
                    true
            ));
        }
        return List.copyOf(items);
    }

    private CurriculumContext loadContext(long userId) {
        LevelTestSubmitResult result = levelTestSubmitService.findResult(userId);
        MainChapter foundation = requireFoundation();
        Map<Long, MainChapter> activeAssets = indexActiveAssets();
        validateChapterResults(result.chapterResults());
        validateCurrentAssets(result.chapterResults(), activeAssets);
        return new CurriculumContext(
                foundation,
                activeAssets,
                result.chapterResults()
        );
    }

    private MainChapter requireFoundation() {
        List<MainChapter> foundations = mainChapterMapper.findAll(
                ChapterType.FOUNDATION,
                true
        );
        if (foundations.size() != 1) {
            throw configurationInvalid();
        }
        MainChapter foundation = foundations.get(0);
        if (foundation.getMainChapterId() == null
                || foundation.getChapterType() != ChapterType.FOUNDATION
                || !foundation.isRequired()
                || foundation.getTitle() == null
                || foundation.getTitle().isBlank()
                || !foundation.isActive()) {
            throw configurationInvalid();
        }
        return foundation;
    }

    private Map<Long, MainChapter> indexActiveAssets() {
        Map<Long, MainChapter> chapters = new HashMap<>();
        for (MainChapter chapter : mainChapterMapper.findAll(
                ChapterType.ASSET,
                true
        )) {
            if (chapter.getMainChapterId() == null
                    || chapter.getChapterType() != ChapterType.ASSET
                    || chapter.getAssetType() == null
                    || chapter.getTitle() == null
                    || chapter.getTitle().isBlank()
                    || !chapter.isActive()
                    || chapters.put(chapter.getMainChapterId(), chapter) != null) {
                throw configurationInvalid();
            }
        }
        return Map.copyOf(chapters);
    }

    private void validateCurrentAssets(
            List<LevelTestChapterGradingResult> results,
            Map<Long, MainChapter> activeAssets
    ) {
        for (LevelTestChapterGradingResult result : results) {
            MainChapter chapter = activeAssets.get(result.mainChapterId());
            if (chapter != null && chapter.getAssetType() != result.assetType()) {
                throw configurationInvalid();
            }
        }
    }

    private void validateChapterResults(
            List<LevelTestChapterGradingResult> results
    ) {
        if (results.isEmpty()) {
            throw configurationInvalid();
        }
        Set<Long> chapterIds = new HashSet<>();
        for (LevelTestChapterGradingResult result : results) {
            if (result.mainChapterId() <= 0
                    || result.assetType() == null
                    || result.totalCount() <= 0
                    || result.correctCount() < 0
                    || result.correctCount() > result.totalCount()
                    || result.allCorrect()
                    != (result.correctCount() == result.totalCount())
                    || !chapterIds.add(result.mainChapterId())) {
                throw configurationInvalid();
            }
        }
    }

    private List<Long> validateSelection(
            List<Long> mainChapterIds,
            CurriculumContext context
    ) {
        if (mainChapterIds == null) {
            throw invalidSelection();
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long mainChapterId : mainChapterIds) {
            if (mainChapterId == null
                    || mainChapterId <= 0
                    || mainChapterId.equals(
                            context.foundation().getMainChapterId()
                    )
                    || !context.activeAssets().containsKey(mainChapterId)
                    || !uniqueIds.add(mainChapterId)) {
                throw invalidSelection();
            }
        }
        return List.copyOf(mainChapterIds);
    }

    private CurriculumDraftItem foundationItem(MainChapter foundation) {
        return item(
                foundation,
                CurriculumSourceType.FOUNDATION,
                1,
                false
        );
    }

    private CurriculumDraftItem item(
            MainChapter chapter,
            CurriculumSourceType sourceType,
            int displayOrder,
            boolean removable
    ) {
        return new CurriculumDraftItem(
                chapter.getMainChapterId(),
                chapter.getTitle(),
                sourceType,
                displayOrder,
                removable
        );
    }

    private CurriculumDraftCandidate candidate(MainChapter chapter) {
        return new CurriculumDraftCandidate(
                chapter.getMainChapterId(),
                chapter.getTitle()
        );
    }

    private ApiException invalidSelection() {
        return new ApiException(ErrorCode.INVALID_CURRICULUM_SELECTION);
    }

    private ApiException configurationInvalid() {
        return new ApiException(ErrorCode.CURRICULUM_CONFIGURATION_INVALID);
    }

    private record CurriculumContext(
            MainChapter foundation,
            Map<Long, MainChapter> activeAssets,
            List<LevelTestChapterGradingResult> chapterResults
    ) {
        private CurriculumContext {
            activeAssets = Map.copyOf(activeAssets);
            chapterResults = List.copyOf(chapterResults);
        }
    }
}
