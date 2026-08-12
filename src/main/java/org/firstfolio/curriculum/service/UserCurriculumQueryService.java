package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
