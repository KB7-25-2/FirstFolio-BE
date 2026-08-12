package org.firstfolio.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.curriculum.domain.UserCurriculumItem;

import java.time.LocalDateTime;

@Mapper
public interface MainChapterLearningMapper {

    UserCurriculumItem findActiveCurriculumItemForUpdate(
            @Param("userId") long userId,
            @Param("mainChapterId") long mainChapterId
    );

    int countActiveSubChapters(
            @Param("mainChapterId") long mainChapterId
    );

    int countIncompleteActiveSubChapters(
            @Param("userId") long userId,
            @Param("mainChapterId") long mainChapterId
    );

    int completeCurriculumItemIfIncomplete(
            @Param("curriculumItemId") long curriculumItemId,
            @Param("completedAt") LocalDateTime completedAt
    );
}
