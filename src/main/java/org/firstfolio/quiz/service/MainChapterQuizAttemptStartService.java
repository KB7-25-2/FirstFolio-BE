package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.UserCurriculumItem;
import org.firstfolio.learning.mapper.MainChapterLearningMapper;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.domain.QuizAttemptStartResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MainChapterQuizAttemptStartService {

    private final QuizAttemptMapper quizAttemptMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final MainChapterLearningMapper learningMapper;
    private final MainChapterMapper mainChapterMapper;
    private final Clock clock;
    private final QuizQuestionSnapshotCodec snapshotCodec;

    public MainChapterQuizAttemptStartService(
            QuizAttemptMapper quizAttemptMapper,
            QuizQuestionMapper quizQuestionMapper,
            MainChapterLearningMapper learningMapper,
            MainChapterMapper mainChapterMapper,
            Clock clock
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.quizQuestionMapper = quizQuestionMapper;
        this.learningMapper = learningMapper;
        this.mainChapterMapper = mainChapterMapper;
        this.clock = clock;
        this.snapshotCodec = new QuizQuestionSnapshotCodec();
    }

    @Transactional
    public QuizAttemptStartResult start(long userId, long mainChapterId) {
        QuizAttempt inProgress = quizAttemptMapper
                .findInProgressByUserIdAndMainChapterIdForUpdate(
                        userId,
                        mainChapterId
                );
        requireAvailableMainChapter(userId, mainChapterId);

        if (inProgress != null) {
            return restore(inProgress);
        }

        // 존재하지 않는 응시 행은 잠글 수 없으므로 커리큘럼 행을 잠근 뒤 한 번 더
        // 확인해 동시 시작 요청이 진행 중 응시를 중복 생성하지 않게 한다.
        inProgress = quizAttemptMapper
                .findInProgressByUserIdAndMainChapterIdForUpdate(
                        userId,
                        mainChapterId
                );
        if (inProgress != null) {
            return restore(inProgress);
        }

        requireAllSubChaptersCompleted(userId, mainChapterId);
        List<QuizQuestion> questions = quizQuestionMapper
                .findLatestPublishedByMainChapterId(mainChapterId);
        if (questions.isEmpty()) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        QuizAttempt attempt = createAttempt(
                userId,
                mainChapterId,
                questions.size(),
                now
        );

        List<QuizAttemptQuestion> views = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            QuizQuestion question = questions.get(index);
            QuizAnswer answer = new QuizAnswer();
            answer.setAttemptId(attempt.getAttemptId());
            answer.setQuestionId(question.getQuestionId());
            answer.setDisplayOrder(index + 1);
            answer.setQuestionSnapshotJson(
                    snapshotCodec.createSnapshot(question)
            );
            answer.setCreatedAt(now);

            if (quizAttemptMapper.insertAnswer(answer) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
            views.add(snapshotCodec.toQuestionView(answer));
        }

        return new QuizAttemptStartResult(attempt, views);
    }

    private void requireAvailableMainChapter(long userId, long mainChapterId) {
        UserCurriculumItem item = learningMapper
                .findActiveCurriculumItemForUpdate(userId, mainChapterId);
        MainChapter mainChapter = mainChapterMapper.findById(mainChapterId);
        if (item == null || mainChapter == null || !mainChapter.isActive()) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }
    }

    private void requireAllSubChaptersCompleted(
            long userId,
            long mainChapterId
    ) {
        if (learningMapper.countActiveSubChapters(mainChapterId) <= 0) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }
        if (learningMapper.countIncompleteActiveSubChapters(
                userId,
                mainChapterId
        ) > 0) {
            throw new ApiException(ErrorCode.SUB_CHAPTERS_INCOMPLETE);
        }
    }

    private QuizAttemptStartResult restore(QuizAttempt attempt) {
        List<QuizAnswer> answers = quizAttemptMapper.findAnswersByAttemptId(
                attempt.getAttemptId()
        );
        if (answers.size() != attempt.getTotalCount()) {
            throw new ApiException(ErrorCode.CONTENT_UNAVAILABLE);
        }
        return new QuizAttemptStartResult(
                attempt,
                answers.stream().map(snapshotCodec::toQuestionView).toList()
        );
    }

    private QuizAttempt createAttempt(
            long userId,
            long mainChapterId,
            int totalCount,
            LocalDateTime startedAt
    ) {
        Integer maxAttemptNo = quizAttemptMapper
                .findMaxAttemptNoByUserIdAndMainChapterId(
                        userId,
                        mainChapterId
                );

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setQuizType(QuizType.MAIN_CHAPTER);
        attempt.setMainChapterId(mainChapterId);
        attempt.setAttemptNo(maxAttemptNo == null ? 1 : maxAttemptNo + 1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(totalCount);
        attempt.setCorrectCount(0);
        attempt.setScore(0);
        attempt.setStartedAt(startedAt);

        if (quizAttemptMapper.insertAttempt(attempt) != 1
                || attempt.getAttemptId() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return attempt;
    }
}
