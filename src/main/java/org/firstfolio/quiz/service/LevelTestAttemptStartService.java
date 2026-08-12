package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAttemptQuestion;
import org.firstfolio.quiz.domain.LevelTestAttemptStartResult;
import org.firstfolio.quiz.domain.LevelTestQuestionSet;
import org.firstfolio.quiz.domain.LevelTestSavedAnswer;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LevelTestAttemptStartService {

    private final QuizAttemptMapper quizAttemptMapper;
    private final LevelTestQueryService levelTestQueryService;
    private final Clock clock;
    private final LevelTestQuestionSnapshotCodec snapshotCodec;

    public LevelTestAttemptStartService(
            QuizAttemptMapper quizAttemptMapper,
            LevelTestQueryService levelTestQueryService,
            Clock clock
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.levelTestQueryService = levelTestQueryService;
        this.clock = clock;
        this.snapshotCodec = new LevelTestQuestionSnapshotCodec();
    }

    @Transactional
    public LevelTestAttemptStartResult start(long userId) {
        if (quizAttemptMapper.findUserIdForUpdate(userId) == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        QuizAttempt existing = quizAttemptMapper
                .findLevelTestByUserIdForUpdate(userId);
        if (existing != null) {
            return restore(existing);
        }

        LevelTestQuestionSet questionSet = levelTestQueryService
                .getQuestionSet();
        return create(userId, questionSet);
    }

    private LevelTestAttemptStartResult create(
            long userId,
            LevelTestQuestionSet questionSet
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setQuizType(QuizType.LEVEL_TEST);
        attempt.setAttemptNo(1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(questionSet.questions().size());
        attempt.setCorrectCount(0);
        attempt.setScore(0);
        attempt.setStartedAt(now);

        if (quizAttemptMapper.insertAttempt(attempt) != 1
                || attempt.getAttemptId() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        Map<Long, MainChapter> chapterById = indexChapters(
                questionSet.mainChapters()
        );
        List<LevelTestAttemptQuestion> questionViews = new ArrayList<>();
        for (int index = 0; index < questionSet.questions().size(); index++) {
            QuizQuestion question = questionSet.questions().get(index);
            if (question.getQuestionId() == null) {
                throw invalidQuestionSet();
            }
            MainChapter chapter = chapterById.get(question.getMainChapterId());
            if (chapter == null) {
                throw invalidQuestionSet();
            }

            QuizAnswer answer = new QuizAnswer();
            answer.setAttemptId(attempt.getAttemptId());
            answer.setQuestionId(question.getQuestionId());
            answer.setDisplayOrder(index + 1);
            answer.setQuestionSnapshotJson(
                    snapshotCodec.createSnapshot(question, chapter)
            );
            answer.setCreatedAt(now);

            if (quizAttemptMapper.insertAnswer(answer) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
            questionViews.add(snapshotCodec.toQuestionView(answer));
        }

        return new LevelTestAttemptStartResult(
                attempt,
                questionViews,
                List.of()
        );
    }

    private LevelTestAttemptStartResult restore(QuizAttempt attempt) {
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.LEVEL_TEST_ALREADY_COMPLETED);
        }
        if (attempt.getQuizType() != QuizType.LEVEL_TEST
                || attempt.getMainChapterId() != null
                || attempt.getSubChapterId() != null
                || attempt.getContentVersionId() != null
                || attempt.getAttemptNo() != 1
                || attempt.getTotalCount() <= 0) {
            throw invalidQuestionSet();
        }

        List<QuizAnswer> storedAnswers = quizAttemptMapper
                .findAnswersByAttemptId(attempt.getAttemptId());
        if (storedAnswers.size() != attempt.getTotalCount()) {
            throw invalidQuestionSet();
        }

        Set<Long> questionIds = new HashSet<>();
        List<LevelTestAttemptQuestion> questions = new ArrayList<>();
        List<LevelTestSavedAnswer> answers = new ArrayList<>();
        for (int index = 0; index < storedAnswers.size(); index++) {
            QuizAnswer storedAnswer = storedAnswers.get(index);
            if (storedAnswer.getAttemptId() != attempt.getAttemptId()
                    || storedAnswer.getDisplayOrder() != index + 1
                    || storedAnswer.getCorrect() != null
                    || !questionIds.add(storedAnswer.getQuestionId())) {
                throw invalidQuestionSet();
            }
            questions.add(snapshotCodec.toQuestionView(storedAnswer));
            LevelTestSavedAnswer saved = snapshotCodec.toSavedAnswer(
                    storedAnswer
            );
            if (saved != null) {
                answers.add(saved);
            }
        }

        return new LevelTestAttemptStartResult(attempt, questions, answers);
    }

    private Map<Long, MainChapter> indexChapters(List<MainChapter> chapters) {
        Map<Long, MainChapter> chapterById = new HashMap<>();
        for (MainChapter chapter : chapters) {
            if (chapter.getMainChapterId() == null
                    || chapter.getAssetType() == null
                    || chapterById.put(chapter.getMainChapterId(), chapter) != null) {
                throw invalidQuestionSet();
            }
        }
        return chapterById;
    }

    private ApiException invalidQuestionSet() {
        return new ApiException(ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID);
    }
}
