package org.firstfolio.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionReference;

import java.util.List;

@Mapper
public interface QuizQuestionMapper {

    QuizQuestion findById(@Param("questionId") long questionId);

    QuizQuestion findLatestByQuestionKeyForUpdate(
            @Param("questionKey") String questionKey
    );

    int countByQuestionKey(@Param("questionKey") String questionKey);

    List<QuizQuestionReference> findReferencesByIds(
            @Param("questionIds") List<Long> questionIds
    );

    List<QuizQuestion> findAllByIds(
            @Param("questionIds") List<Long> questionIds
    );

    int insert(QuizQuestion question);
}
