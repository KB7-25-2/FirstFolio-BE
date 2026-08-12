package org.firstfolio.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.mapper.LearningProgressMapper;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.domain.QuizAttemptStartResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class QuizAttemptStartService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final QuizAttemptMapper quizAttemptMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final LearningProgressMapper learningProgressMapper;
    private final ContentVersionMapper contentVersionMapper;
    private final SubChapterMapper subChapterMapper;
    private final StaticContentStorage contentStorage;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final QuizQuestionSnapshotCodec snapshotCodec;

    public QuizAttemptStartService(
            QuizAttemptMapper quizAttemptMapper,
            QuizQuestionMapper quizQuestionMapper,
            LearningProgressMapper learningProgressMapper,
            ContentVersionMapper contentVersionMapper,
            SubChapterMapper subChapterMapper,
            StaticContentStorage contentStorage,
            Clock clock
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.quizQuestionMapper = quizQuestionMapper;
        this.learningProgressMapper = learningProgressMapper;
        this.contentVersionMapper = contentVersionMapper;
        this.subChapterMapper = subChapterMapper;
        this.contentStorage = contentStorage;
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
        this.snapshotCodec = new QuizQuestionSnapshotCodec();
    }

    @Transactional
    public QuizAttemptStartResult start(long userId, long subChapterId) {
        requireActiveSubChapter(subChapterId);
        LearningProgress progress = requireCompletedProgress(userId, subChapterId);

        QuizAttempt inProgress = quizAttemptMapper
                .findInProgressByUserIdAndSubChapterIdForUpdate(
                        userId,
                        subChapterId
                );
        if (inProgress != null) {
            return restore(inProgress);
        }

        List<Long> questionIds = loadQuestionIds(
                subChapterId,
                progress.getContentVersionId()
        );
        List<QuizQuestion> questions = requireAvailableQuestions(
                subChapterId,
                questionIds
        );

        LocalDateTime now = LocalDateTime.now(clock);
        QuizAttempt attempt = createAttempt(
                userId,
                subChapterId,
                progress.getContentVersionId(),
                questionIds.size(),
                now
        );

        Map<Long, QuizQuestion> questionById = indexQuestions(questions);
        List<QuizAttemptQuestion> views = new ArrayList<>();
        for (int index = 0; index < questionIds.size(); index++) {
            long questionId = questionIds.get(index);
            int displayOrder = index + 1;
            QuizQuestion question = questionById.get(questionId);
            String snapshot = snapshotCodec.createSnapshot(question);

            QuizAnswer answer = new QuizAnswer();
            answer.setAttemptId(attempt.getAttemptId());
            answer.setQuestionId(questionId);
            answer.setDisplayOrder(displayOrder);
            answer.setQuestionSnapshotJson(snapshot);
            answer.setCreatedAt(now);
            if (quizAttemptMapper.insertAnswer(answer) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }

            views.add(snapshotCodec.toQuestionView(answer));
        }

        return new QuizAttemptStartResult(attempt, views);
    }

    private LearningProgress requireCompletedProgress(
            long userId,
            long subChapterId
    ) {
        LearningProgress progress = learningProgressMapper
                .findByUserIdAndSubChapterIdForUpdate(userId, subChapterId);
        if (progress == null
                || progress.getStatus() != LearningProgressStatus.COMPLETED) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }
        return progress;
    }

    private QuizAttemptStartResult restore(QuizAttempt attempt) {
        List<QuizAnswer> answers = quizAttemptMapper.findAnswersByAttemptId(
                attempt.getAttemptId()
        );
        if (answers.size() != attempt.getTotalCount()) {
            throw unavailable(null);
        }

        List<QuizAttemptQuestion> questions = answers.stream()
                .map(snapshotCodec::toQuestionView)
                .toList();
        return new QuizAttemptStartResult(attempt, questions);
    }

    private List<Long> loadQuestionIds(
            long subChapterId,
            long contentVersionId
    ) {
        ContentVersion version = contentVersionMapper.findById(contentVersionId);
        if (version == null || version.getSubChapterId() != subChapterId) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }

        StoredContent stored;
        try {
            stored = contentStorage.load(new StoredObjectRef(
                    version.getStorageObjectKey(),
                    version.getStorageVersionId()
            ));
        } catch (ContentStorageException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
        if (!isJson(stored.contentType())) {
            throw unavailable(null);
        }

        try {
            JsonNode lesson = objectMapper.readTree(new String(
                    stored.content(),
                    StandardCharsets.UTF_8
            ));
            if (lesson == null
                    || !lesson.isObject()
                    || !Objects.equals(
                            version.getSchemaVersion(),
                            lesson.path("schemaVersion").textValue()
                    )) {
                throw unavailable(null);
            }

            JsonNode questionIdsNode = lesson.path("subChapterQuiz")
                    .path("questionIds");
            if (!questionIdsNode.isArray() || questionIdsNode.isEmpty()) {
                throw unavailable(null);
            }

            List<Long> questionIds = new ArrayList<>();
            for (JsonNode questionId : questionIdsNode) {
                if (!questionId.canConvertToLong() || questionId.longValue() <= 0) {
                    throw unavailable(null);
                }
                questionIds.add(questionId.longValue());
            }
            if (questionIds.stream().distinct().count() != questionIds.size()) {
                throw unavailable(null);
            }
            return List.copyOf(questionIds);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private List<QuizQuestion> requireAvailableQuestions(
            long subChapterId,
            List<Long> questionIds
    ) {
        List<QuizQuestion> questions = quizQuestionMapper.findAllByIds(questionIds);
        Map<Long, QuizQuestion> questionById = indexQuestions(questions);

        for (Long questionId : questionIds) {
            QuizQuestion question = questionById.get(questionId);
            if (question == null
                    || question.getUsageType() != QuizUsageType.SUB_CHAPTER
                    || !Objects.equals(question.getSubChapterId(), subChapterId)
                    || question.getStatus() != QuizQuestionStatus.PUBLISHED) {
                throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
            }
        }
        return questions;
    }

    private QuizAttempt createAttempt(
            long userId,
            long subChapterId,
            long contentVersionId,
            int totalCount,
            LocalDateTime startedAt
    ) {
        Integer maxAttemptNo = quizAttemptMapper
                .findMaxAttemptNoByUserIdAndSubChapterId(userId, subChapterId);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setQuizType(QuizType.SUB_CHAPTER);
        attempt.setSubChapterId(subChapterId);
        attempt.setContentVersionId(contentVersionId);
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

    private Map<Long, QuizQuestion> indexQuestions(List<QuizQuestion> questions) {
        Map<Long, QuizQuestion> questionById = new HashMap<>();
        for (QuizQuestion question : questions) {
            questionById.put(question.getQuestionId(), question);
        }
        return questionById;
    }

    private boolean isJson(String contentType) {
        return JSON_CONTENT_TYPE.equalsIgnoreCase(
                contentType.split(";", 2)[0].trim()
        );
    }

    private void requireActiveSubChapter(long subChapterId) {
        SubChapter subChapter = subChapterMapper.findById(subChapterId);
        if (subChapter == null || !subChapter.isActive()) {
            throw new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE);
        }
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(
                ErrorCode.CONTENT_UNAVAILABLE,
                ErrorCode.CONTENT_UNAVAILABLE.getDefaultMessage(),
                cause
        );
    }
}
