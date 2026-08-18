package org.firstfolio.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.learning.domain.LearningContinueCandidate;
import org.firstfolio.learning.domain.MainChapterQuizContinueCandidate;

@Mapper
public interface LearningContinueMapper {

    LearningContinueCandidate findLatestInProgress(
            @Param("userId") long userId
    );

    SubChapterQuizContinueCandidate findSubChapterQuizCandidate(
            @Param("userId") long userId
    );

    MainChapterQuizContinueCandidate findMainChapterQuizCandidate(
            @Param("userId") long userId
    );

    record SubChapterQuizContinueCandidate(
            long curriculumItemId,
            long mainChapterId,
            long subChapterId,
            Long attemptId
    ) {
    }
}
