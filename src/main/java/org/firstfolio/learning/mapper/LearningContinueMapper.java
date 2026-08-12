package org.firstfolio.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.learning.domain.LearningContinueCandidate;

@Mapper
public interface LearningContinueMapper {

    LearningContinueCandidate findLatestInProgress(
            @Param("userId") long userId
    );
}
