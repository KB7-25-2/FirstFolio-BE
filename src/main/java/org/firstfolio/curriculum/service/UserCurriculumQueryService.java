package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumOverviewItem;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserCurriculumQueryService {

    private final UserCurriculumMapper userCurriculumMapper;
    private final MainChapterMapper mainChapterMapper;

    public UserCurriculumQueryService(
            UserCurriculumMapper userCurriculumMapper,
            MainChapterMapper mainChapterMapper
    ) {
        this.userCurriculumMapper = userCurriculumMapper;
        this.mainChapterMapper = mainChapterMapper;
    }

    @Transactional(readOnly = true)
    public List<UserCurriculumItem> findConfirmedCurriculum(long userId) {
        List<UserCurriculumItem> items = userCurriculumMapper
                .findActiveByUserId(userId);
        if (items.isEmpty()) {
            return List.of();
        }

        List<MainChapter> foundations = mainChapterMapper.findAll(
                ChapterType.FOUNDATION,
                true
        );
        if (foundations.size() != 1 || !hasRequiredFoundation(
                items.get(0),
                foundations.get(0)
        )) {
            throw new ApiException(ErrorCode.CURRICULUM_CONFIGURATION_INVALID);
        }

        return List.copyOf(items);
    }

    @Transactional(readOnly = true)
    public List<CurriculumOverviewItem> findOverview(long userId) {
        List<CurriculumOverviewItem> items = userCurriculumMapper
                .findOverviewByUserId(userId);
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.CURRICULUM_NOT_FOUND);
        }
        validateOverview(items);
        return List.copyOf(items);
    }

    private void validateOverview(List<CurriculumOverviewItem> items) {
        Set<Long> curriculumItemIds = new HashSet<>();
        Set<Long> chapterIds = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            CurriculumOverviewItem item = items.get(index);
            boolean foundation = index == 0;
            if (item.curriculumItemId() <= 0
                    || item.mainChapterId() <= 0
                    || item.title() == null
                    || item.title().isBlank()
                    || item.displayOrder() != index + 1
                    || item.status() != CurriculumItemStatus.ACTIVE
                    || item.sourceType() == null
                    || item.chapterType() == null
                    || item.progressPercent() < 0
                    || item.progressPercent() > 100
                    || (item.completedAt() != null
                    && item.progressPercent() != 100)
                    || !curriculumItemIds.add(item.curriculumItemId())
                    || !chapterIds.add(item.mainChapterId())
                    || !hasValidChapterRole(item, foundation)) {
                throw new ApiException(
                        ErrorCode.CURRICULUM_CONFIGURATION_INVALID
                );
            }
        }
    }

    private boolean hasValidChapterRole(
            CurriculumOverviewItem item,
            boolean foundation
    ) {
        if (foundation) {
            return item.chapterType() == ChapterType.FOUNDATION
                    && item.sourceType() == CurriculumSourceType.FOUNDATION;
        }
        return item.chapterType() == ChapterType.ASSET
                && item.sourceType() != CurriculumSourceType.FOUNDATION;
    }

    private boolean hasRequiredFoundation(
            UserCurriculumItem first,
            MainChapter foundation
    ) {
        return first.getMainChapterId() == foundation.getMainChapterId()
                && first.getDisplayOrder() == 1
                && first.getSourceType() == CurriculumSourceType.FOUNDATION
                && first.getStatus() == CurriculumItemStatus.ACTIVE;
    }
}
