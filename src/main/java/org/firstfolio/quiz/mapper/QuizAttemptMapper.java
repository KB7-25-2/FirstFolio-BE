package org.firstfolio.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;

import java.util.List;

@Mapper
public interface QuizAttemptMapper {

    QuizAttempt findByIdForUpdate(@Param("attemptId") long attemptId);

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

    QuizAnswer findAnswerByAttemptIdAndQuestionIdForUpdate(
            @Param("attemptId") long attemptId,
            @Param("questionId") long questionId
    );

    int countAnsweredByAttemptId(@Param("attemptId") long attemptId);

    int countCorrectByAttemptId(@Param("attemptId") long attemptId);

    int insertAttempt(QuizAttempt attempt);

    int insertAnswer(QuizAnswer answer);

    int gradeAnswerIfUnanswered(QuizAnswer answer);

    int completeAttemptIfInProgress(QuizAttempt attempt);
}
