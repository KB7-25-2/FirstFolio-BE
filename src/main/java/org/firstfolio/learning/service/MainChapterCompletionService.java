package org.firstfolio.learning.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.MainChapterCompletionResult;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.learning.mapper.MainChapterLearningMapper;
import org.firstfolio.portfolio.service.InitialGrantResult;
import org.firstfolio.portfolio.service.InitialGrantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MainChapterCompletionService {

    private final MainChapterLearningMapper learningMapper;
    private final MainChapterMapper mainChapterMapper;
    private final InitialGrantService initialGrantService;

    public MainChapterCompletionService(
            MainChapterLearningMapper learningMapper,
            MainChapterMapper mainChapterMapper,
            InitialGrantService initialGrantService
    ) {
        this.learningMapper = learningMapper;
        this.mainChapterMapper = mainChapterMapper;
        this.initialGrantService = initialGrantService;
    }

    @Transactional
    public MainChapterCompletionResult complete(
            long userId,
            long mainChapterId,
            LocalDateTime completedAt
    ) {
        if (completedAt == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        UserCurriculumItem item = learningMapper
                .findActiveCurriculumItemForUpdate(userId, mainChapterId);
        MainChapter chapter = mainChapterMapper.findById(mainChapterId);
        if (item == null || chapter == null || !chapter.isActive()) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }

        boolean completedNow = item.getCompletedAt() == null;
        if (completedNow && learningMapper.completeCurriculumItemIfIncomplete(
                item.getCurriculumItemId(),
                completedAt
        ) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        InitialGrantResult foundationGrant = null;
        if (chapter.getChapterType() == ChapterType.FOUNDATION) {
            foundationGrant = initialGrantService.grantOnFoundationCompleted(
                    userId,
                    item.getCurriculumItemId()
            );
        }
        return new MainChapterCompletionResult(
                chapter.getChapterType(),
                completedNow,
                foundationGrant
        );
    }
}
