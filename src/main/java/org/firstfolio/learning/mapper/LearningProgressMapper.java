package org.firstfolio.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressEvent;

@Mapper
public interface LearningProgressMapper {

    LearningProgress findByUserIdAndSubChapterId(
            @Param("userId") long userId,
            @Param("subChapterId") long subChapterId
    );

    LearningProgress findByUserIdAndSubChapterIdForUpdate(
            @Param("userId") long userId,
            @Param("subChapterId") long subChapterId
    );

    int insertIfAbsent(LearningProgress progress);

    int updateProgress(LearningProgress progress);

    int insertEvent(LearningProgressEvent event);
}
