package org.firstfolio.quiz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionReference;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface QuizQuestionMapper {

    QuizQuestion findById(@Param("questionId") long questionId);

    QuizQuestion findByIdForUpdate(@Param("questionId") long questionId);

    QuizQuestion findLatestByQuestionKeyForUpdate(
            @Param("questionKey") String questionKey
    );

    List<QuizQuestion> findPublishedByQuestionKeyForUpdate(
            @Param("questionKey") String questionKey
    );

    int countByQuestionKey(@Param("questionKey") String questionKey);

    List<QuizQuestionReference> findReferencesByIds(
            @Param("questionIds") List<Long> questionIds
    );

    List<QuizQuestion> findAllByIds(
            @Param("questionIds") List<Long> questionIds
    );

    List<QuizQuestion> findLatestPublishedByMainChapterId(
            @Param("mainChapterId") long mainChapterId
    );

    List<QuizQuestion> findLatestPublishedLevelTestQuestions();

    List<QuizQuestion> findLatestPublishedDailyGeneralQuestions();

    QuizQuestion findLatestPublishedDailyNewsByQuestDate(
            @Param("questDate") LocalDate questDate
    );

    List<QuizQuestion> findPage(
            @Param("usageType") QuizUsageType usageType,
            @Param("mainChapterId") Long mainChapterId,
            @Param("subChapterId") Long subChapterId,
            @Param("status") QuizQuestionStatus status,
            @Param("questionKey") String questionKey,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );

    int publishDraft(
            @Param("questionId") long questionId,
            @Param("publishedAt") java.time.LocalDateTime publishedAt
    );

    int retirePublished(@Param("questionId") long questionId);

    int insert(QuizQuestion question);
}
