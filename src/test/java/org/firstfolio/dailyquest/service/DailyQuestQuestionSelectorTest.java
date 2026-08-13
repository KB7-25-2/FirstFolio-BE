package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuestWrongAnswer;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyQuestQuestionSelectorTest {

    private final DailyQuestQuestionSelector selector =
            new DailyQuestQuestionSelector();

    @Test
    void selectsWeakSubChaptersThenWeakMainChapters() {
        List<QuizQuestion> candidates = List.of(
                question(1L, "general-1", 10L, 101L),
                question(2L, "general-2", 10L, 102L),
                question(3L, "general-3", 20L, 201L),
                question(4L, "general-4", 20L, null),
                question(5L, "general-5", 30L, null)
        );
        List<DailyQuestWrongAnswer> wrongAnswers = List.of(
                wrong("wrong-102", 10L, 102L, 12),
                wrong("wrong-101-a", 10L, 101L, 11),
                wrong("wrong-101-b", 10L, 101L, 10),
                wrong("wrong-20", 20L, null, 9)
        );

        List<QuizQuestion> selected = selector.select(
                candidates,
                wrongAnswers,
                Set.of()
        );

        assertEquals(
                List.of(2L, 1L, 3L, 4L),
                selected.stream().map(QuizQuestion::getQuestionId).toList()
        );
    }

    @Test
    void excludesRecentQuestionsBeforeRelaxingTheSevenDayRule() {
        List<QuizQuestion> candidates = List.of(
                question(1L, "recent-weak", 10L, 101L),
                question(2L, "fresh-2", 20L, null),
                question(3L, "fresh-3", 30L, null),
                question(4L, "fresh-4", 40L, null)
        );
        List<DailyQuestWrongAnswer> wrongAnswers = List.of(
                wrong("wrong-101", 10L, 101L, 12)
        );

        List<QuizQuestion> selected = selector.select(
                candidates,
                wrongAnswers,
                Set.of("recent-weak")
        );

        assertEquals(
                List.of(2L, 3L, 4L, 1L),
                selected.stream().map(QuizQuestion::getQuestionId).toList()
        );
    }

    @Test
    void usesWrongCountWhenWeaknessesHaveTheSameLatestTime() {
        DailyQuestWrongAnswer fewer = wrong(
                "wrong-101",
                10L,
                101L,
                12
        );
        DailyQuestWrongAnswer more = wrong(
                "wrong-201",
                20L,
                201L,
                12
        );
        more.setWrongCount(3);

        List<QuizQuestion> selected = selector.select(
                List.of(
                        question(1L, "general-101", 10L, 101L),
                        question(2L, "general-201", 20L, 201L),
                        question(3L, "general-3", 30L, null),
                        question(4L, "general-4", 40L, null)
                ),
                List.of(fewer, more),
                Set.of()
        );

        assertEquals(
                List.of(2L, 1L, 3L, 4L),
                selected.stream().map(QuizQuestion::getQuestionId).toList()
        );
    }

    @Test
    void rejectsPoolWhenFourDistinctQuestionKeysCannotBeSelected() {
        List<QuizQuestion> candidates = List.of(
                question(1L, "general-1", 10L, null),
                question(2L, "general-1", 10L, null),
                question(3L, "general-2", 20L, null),
                question(4L, "general-3", 30L, null)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> selector.select(candidates, List.of(), Set.of())
        );

        assertEquals(
                ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE,
                exception.getErrorCode()
        );
    }

    private QuizQuestion question(
            long questionId,
            String questionKey,
            long mainChapterId,
            Long subChapterId
    ) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(questionId);
        question.setQuestionKey(questionKey);
        question.setVersionNo(1);
        question.setUsageType(QuizUsageType.DAILY_GENERAL);
        question.setMainChapterId(mainChapterId);
        question.setSubChapterId(subChapterId);
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private DailyQuestWrongAnswer wrong(
            String questionKey,
            long mainChapterId,
            Long subChapterId,
            int hour
    ) {
        DailyQuestWrongAnswer wrongAnswer = new DailyQuestWrongAnswer();
        wrongAnswer.setQuestionKey(questionKey);
        wrongAnswer.setMainChapterId(mainChapterId);
        wrongAnswer.setSubChapterId(subChapterId);
        wrongAnswer.setAnsweredAt(LocalDateTime.of(
                2026,
                8,
                12,
                hour,
                0
        ));
        wrongAnswer.setWrongCount(1);
        return wrongAnswer;
    }
}
