package org.firstfolio.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;

import java.util.List;

@Mapper
public interface QuizAttemptMapper {

    QuizAttempt findInProgressByUserIdAndSubChapterIdForUpdate(
            @Param("userId") long userId,
            @Param("subChapterId") long subChapterId
    );

    Integer findMaxAttemptNoByUserIdAndSubChapterId(
            @Param("userId") long userId,
            @Param("subChapterId") long subChapterId
    );

    List<QuizAnswer> findAnswersByAttemptId(
            @Param("attemptId") long attemptId
    );

    int insertAttempt(QuizAttempt attempt);

    int insertAnswer(QuizAnswer answer);
}
