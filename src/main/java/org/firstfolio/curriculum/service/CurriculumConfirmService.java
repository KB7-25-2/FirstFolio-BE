package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CurriculumConfirmService {

    private final UserCurriculumMapper userCurriculumMapper;
    private final CurriculumDraftService curriculumDraftService;
    private final Clock clock;

    public CurriculumConfirmService(
            UserCurriculumMapper userCurriculumMapper,
            CurriculumDraftService curriculumDraftService,
            Clock clock
    ) {
        this.userCurriculumMapper = userCurriculumMapper;
        this.curriculumDraftService = curriculumDraftService;
        this.clock = clock;
    }

    @Transactional
    public List<CurriculumDraftItem> confirm(
            long userId,
            List<Long> mainChapterIds
    ) {
        if (userCurriculumMapper.findUserIdForUpdate(userId) == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        List<CurriculumDraftItem> items = curriculumDraftService.editDraft(
                userId,
                mainChapterIds
        );
        List<UserCurriculumItem> existing = userCurriculumMapper
                .findActiveByUserId(userId);
        if (!existing.isEmpty()) {
            if (sameCurriculum(existing, items)) {
                return items;
            }
            throw new ApiException(ErrorCode.CURRICULUM_ALREADY_CONFIRMED);
        }

        int inserted = userCurriculumMapper.insertAll(
                userId,
                items,
                LocalDateTime.now(clock)
        );
        if (inserted != items.size()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return items;
    }

    private boolean sameCurriculum(
            List<UserCurriculumItem> existing,
            List<CurriculumDraftItem> requested
    ) {
        if (existing.size() != requested.size()) {
            return false;
        }
        for (int index = 0; index < existing.size(); index++) {
            UserCurriculumItem saved = existing.get(index);
            CurriculumDraftItem item = requested.get(index);
            if (saved.getMainChapterId() != item.mainChapterId()
                    || saved.getDisplayOrder() != item.displayOrder()
                    || saved.getSourceType() != item.sourceType()) {
                return false;
            }
        }
        return true;
    }
}
