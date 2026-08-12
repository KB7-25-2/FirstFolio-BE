package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAttemptAggregate;
import org.firstfolio.quiz.domain.LevelTestQuestionSet;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LevelTestQueryService {

    private final MainChapterMapper mainChapterMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final QuizAttemptMapper quizAttemptMapper;

    public LevelTestQueryService(
            MainChapterMapper mainChapterMapper,
            QuizQuestionMapper quizQuestionMapper,
            QuizAttemptMapper quizAttemptMapper
    ) {
        this.mainChapterMapper = mainChapterMapper;
        this.quizQuestionMapper = quizQuestionMapper;
        this.quizAttemptMapper = quizAttemptMapper;
    }

    @Transactional(readOnly = true)
    public LevelTestQuestionSet getQuestionSet() {
        List<MainChapter> chapters = mainChapterMapper.findAll(
                ChapterType.ASSET,
                true
        );
        List<QuizQuestion> questions = quizQuestionMapper
                .findLatestPublishedLevelTestQuestions();
        validateQuestionSet(chapters, questions);
        return new LevelTestQuestionSet(chapters, questions);
    }

    @Transactional(readOnly = true)
    public LevelTestAttemptAggregate findAttempt(long userId) {
        QuizAttempt attempt = quizAttemptMapper.findLevelTestByUserId(userId);
        if (attempt == null) {
            return null;
        }
        if (attempt.getQuizType() != QuizType.LEVEL_TEST
                || attempt.getMainChapterId() != null
                || attempt.getSubChapterId() != null
                || attempt.getTotalCount() <= 0) {
            throw invalidQuestionSet();
        }

        List<QuizAnswer> answers = quizAttemptMapper.findAnswersByAttemptId(
                attempt.getAttemptId()
        );
        if (answers.size() != attempt.getTotalCount()) {
            throw invalidQuestionSet();
        }
        return new LevelTestAttemptAggregate(attempt, answers);
    }

    private void validateQuestionSet(
            List<MainChapter> chapters,
            List<QuizQuestion> questions
    ) {
        if (chapters.isEmpty() || questions.isEmpty()) {
            throw invalidQuestionSet();
        }

        Set<Long> activeChapterIds = chapters.stream()
                .map(MainChapter::getMainChapterId)
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> questionChapterIds = questions.stream()
                .map(question -> validateQuestion(question, activeChapterIds))
                .collect(Collectors.toUnmodifiableSet());

        if (!questionChapterIds.equals(activeChapterIds)) {
            throw invalidQuestionSet();
        }
    }

    private Long validateQuestion(
            QuizQuestion question,
            Set<Long> activeChapterIds
    ) {
        Long mainChapterId = question.getMainChapterId();
        if (question.getUsageType() != QuizUsageType.LEVEL_TEST
                || question.getStatus() != QuizQuestionStatus.PUBLISHED
                || mainChapterId == null
                || !activeChapterIds.contains(mainChapterId)) {
            throw invalidQuestionSet();
        }
        return mainChapterId;
    }

    private ApiException invalidQuestionSet() {
        return new ApiException(ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID);
    }
}
