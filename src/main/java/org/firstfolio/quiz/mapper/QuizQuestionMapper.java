package org.firstfolio.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.quiz.domain.QuizQuestionReference;

import java.util.List;

@Mapper
public interface QuizQuestionMapper {

    List<QuizQuestionReference> findReferencesByIds(
            @Param("questionIds") List<Long> questionIds
    );
}
