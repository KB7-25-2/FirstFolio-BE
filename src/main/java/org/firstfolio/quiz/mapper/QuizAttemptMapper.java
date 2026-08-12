package org.firstfolio.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;

import java.util.List;

@Mapper
public interface QuizAttemptMapper {

    Long findUserIdForUpdate(@Param("userId") long userId);

    QuizAttempt findByIdForUpdate(@Param("attemptId") long attemptId);

    QuizAttempt findLevelTestByUserId(@Param("userId") long userId);

    QuizAttempt findLevelTestByUserIdForUpdate(@Param("userId") long userId);

    QuizAttempt findInProgressByUserIdAndSubChapterIdForUpdate(
            @Param("userId") long userId,
            @Param("subChapterId") long subChapterId
    );

    QuizAttempt findInProgressByUserIdAndMainChapterIdForUpdate(
            @Param("userId") long userId,
            @Param("mainChapterId") long mainChapterId
    );

    Integer findMaxAttemptNoByUserIdAndSubChapterId(
            @Param("userId") long userId,
            @Param("subChapterId") long subChapterId
    );

    Integer findMaxAttemptNoByUserIdAndMainChapterId(
            @Param("userId") long userId,
            @Param("mainChapterId") long mainChapterId
    );

    List<QuizAnswer> findAnswersByAttemptId(
            @Param("attemptId") long attemptId
    );

    List<QuizAnswer> findAnswersByAttemptIdForUpdate(
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

    int saveLevelTestAnswer(QuizAnswer answer);

    int gradeLevelTestAnswer(QuizAnswer answer);

    int gradeAnswerIfUnanswered(QuizAnswer answer);

    int completeAttemptIfInProgress(QuizAttempt attempt);
}
