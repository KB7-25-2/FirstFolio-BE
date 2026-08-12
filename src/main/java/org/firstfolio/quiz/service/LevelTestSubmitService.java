package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestChapterGradingResult;
import org.firstfolio.quiz.domain.LevelTestQuestionGradingResult;
import org.firstfolio.quiz.domain.LevelTestSubmitResult;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LevelTestSubmitService {

    private final QuizAttemptMapper quizAttemptMapper;
    private final Clock clock;
    private final LevelTestQuestionSnapshotCodec snapshotCodec;

    public LevelTestSubmitService(
            QuizAttemptMapper quizAttemptMapper,
            Clock clock
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.clock = clock;
        this.snapshotCodec = new LevelTestQuestionSnapshotCodec();
    }

    @Transactional
    public LevelTestSubmitResult submit(long userId, long attemptId) {
        QuizAttempt attempt = quizAttemptMapper.findByIdForUpdate(attemptId);
        requireLevelTestOwner(attempt, userId);

        List<QuizAnswer> answers = quizAttemptMapper
                .findAnswersByAttemptIdForUpdate(attemptId);
        validateAssignedAnswers(attempt, answers);

        if (attempt.getStatus() == QuizAttemptStatus.GRADED) {
            return restore(attempt, answers);
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_GRADED);
        }
        if (answers.stream().anyMatch(
                answer -> answer.getUserAnswerJson() == null
        )) {
            throw new ApiException(ErrorCode.REQUIRED_ANSWERS_MISSING);
        }
        if (answers.stream().anyMatch(answer -> answer.getCorrect() != null)) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        List<LevelTestQuestionGradingResult> questionResults = answers.stream()
                .map(snapshotCodec::grade)
                .toList();
        for (int index = 0; index < answers.size(); index++) {
            QuizAnswer answer = answers.get(index);
            answer.setCorrect(questionResults.get(index).correct());
        }

        for (QuizAnswer answer : answers) {
            if (quizAttemptMapper.gradeLevelTestAnswer(answer) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }

        int correctCount = (int) questionResults.stream()
                .filter(LevelTestQuestionGradingResult::correct)
                .count();
        attempt.setStatus(QuizAttemptStatus.GRADED);
        attempt.setCorrectCount(correctCount);
        attempt.setScore(score(correctCount, attempt.getTotalCount()));
        attempt.setRewardPolicyId(null);
        attempt.setPointTransactionId(null);
        attempt.setSubmittedAt(LocalDateTime.now(clock));
        if (quizAttemptMapper.completeAttemptIfInProgress(attempt) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        return result(attempt, questionResults);
    }

    private LevelTestSubmitResult restore(
            QuizAttempt attempt,
            List<QuizAnswer> answers
    ) {
        if (attempt.getSubmittedAt() == null
                || answers.stream().anyMatch(
                        answer -> answer.getUserAnswerJson() == null
                                || answer.getCorrect() == null
                )) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        List<LevelTestQuestionGradingResult> questionResults = answers.stream()
                .map(snapshotCodec::grade)
                .toList();
        for (int index = 0; index < answers.size(); index++) {
            if (answers.get(index).getCorrect()
                    != questionResults.get(index).correct()) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }

        int correctCount = (int) questionResults.stream()
                .filter(LevelTestQuestionGradingResult::correct)
                .count();
        if (attempt.getCorrectCount() != correctCount
                || attempt.getScore() != score(correctCount, attempt.getTotalCount())) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return result(attempt, questionResults);
    }

    private LevelTestSubmitResult result(
            QuizAttempt attempt,
            List<LevelTestQuestionGradingResult> questionResults
    ) {
        return new LevelTestSubmitResult(
                attempt.getAttemptId(),
                attempt.getStatus(),
                questionResults,
                aggregateChapters(questionResults)
        );
    }

    private List<LevelTestChapterGradingResult> aggregateChapters(
            List<LevelTestQuestionGradingResult> questionResults
    ) {
        Map<Long, ChapterCounter> chapters = new LinkedHashMap<>();
        for (LevelTestQuestionGradingResult question : questionResults) {
            ChapterCounter counter = chapters.computeIfAbsent(
                    question.mainChapterId(),
                    ignored -> new ChapterCounter(question.assetType())
            );
            if (counter.assetType != question.assetType()) {
                throw new ApiException(ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID);
            }
            counter.totalCount++;
            if (question.correct()) {
                counter.correctCount++;
            }
        }

        List<LevelTestChapterGradingResult> results = new ArrayList<>();
        for (Map.Entry<Long, ChapterCounter> entry : chapters.entrySet()) {
            ChapterCounter counter = entry.getValue();
            results.add(new LevelTestChapterGradingResult(
                    entry.getKey(),
                    counter.assetType,
                    counter.totalCount,
                    counter.correctCount,
                    counter.totalCount == counter.correctCount
            ));
        }
        return List.copyOf(results);
    }

    private void requireLevelTestOwner(QuizAttempt attempt, long userId) {
        if (attempt == null) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
        }
        if (attempt.getUserId() != userId) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN);
        }
        if (attempt.getQuizType() != QuizType.LEVEL_TEST) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
        }
    }

    private void validateAssignedAnswers(
            QuizAttempt attempt,
            List<QuizAnswer> answers
    ) {
        if (attempt.getTotalCount() <= 0
                || answers.size() != attempt.getTotalCount()) {
            throw new ApiException(ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID);
        }
        Set<Long> questionIds = new HashSet<>();
        for (int index = 0; index < answers.size(); index++) {
            QuizAnswer answer = answers.get(index);
            if (answer.getAttemptId() != attempt.getAttemptId()
                    || answer.getDisplayOrder() != index + 1
                    || !questionIds.add(answer.getQuestionId())) {
                throw new ApiException(ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID);
            }
        }
    }

    private int score(int correctCount, int totalCount) {
        return Math.round(correctCount * 100.0f / totalCount);
    }

    private static final class ChapterCounter {
        private final AssetType assetType;
        private int totalCount;
        private int correctCount;

        private ChapterCounter(AssetType assetType) {
            this.assetType = assetType;
        }
    }
}
