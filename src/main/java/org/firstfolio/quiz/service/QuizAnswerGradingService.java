package org.firstfolio.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAnswerGradingResult;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.reward.domain.QuizRewardResult;
import org.firstfolio.reward.service.QuizRewardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class QuizAnswerGradingService {

    private static final int MAX_CHOICE_KEY_LENGTH = 50;

    private final QuizAttemptMapper quizAttemptMapper;
    private final Clock clock;
    private final QuizRewardService quizRewardService;
    private final ObjectMapper objectMapper;

    public QuizAnswerGradingService(
            QuizAttemptMapper quizAttemptMapper,
            Clock clock,
            QuizRewardService quizRewardService
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.clock = clock;
        this.quizRewardService = quizRewardService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public QuizAnswerGradingResult grade(
            long userId,
            long attemptId,
            long questionId,
            String requestedKey
    ) {
        QuizAttempt attempt = quizAttemptMapper.findByIdForUpdate(attemptId);
        if (attempt == null) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
        }
        if (attempt.getUserId() != userId) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN);
        }

        QuizAnswer answer = quizAttemptMapper
                .findAnswerByAttemptIdAndQuestionIdForUpdate(
                        attemptId,
                        questionId
                );
        if (answer == null) {
            throw new ApiException(ErrorCode.QUESTION_NOT_IN_ATTEMPT);
        }

        String selectedKey = normalizeSelectedKey(requestedKey);
        QuestionSnapshot snapshot = parseSnapshot(
                answer.getQuestionSnapshotJson()
        );

        if (answer.getUserAnswerJson() != null) {
            String existingKey = parseUserAnswer(answer.getUserAnswerJson());
            if (!existingKey.equals(selectedKey)) {
                throw new ApiException(ErrorCode.ANSWER_ALREADY_SUBMITTED);
            }
            return result(attempt, answer, snapshot, existingKey);
        }

        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_GRADED);
        }
        if (!snapshot.optionKeys().contains(selectedKey)) {
            throw new ApiException(ErrorCode.INVALID_SELECTED_CHOICE);
        }

        boolean correct = snapshot.correctKey().equals(selectedKey);
        answer.setUserAnswerJson(toUserAnswerJson(selectedKey));
        answer.setCorrect(correct);
        answer.setAnsweredAt(LocalDateTime.now(clock));

        if (quizAttemptMapper.gradeAnswerIfUnanswered(answer) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return completeAndResult(
                attempt,
                answer,
                snapshot,
                selectedKey
        );
    }

    private QuizAnswerGradingResult completeAndResult(
            QuizAttempt attempt,
            QuizAnswer answer,
            QuestionSnapshot snapshot,
            String selectedKey
    ) {
        int answeredCount = answeredCount(attempt);
        if (answeredCount < attempt.getTotalCount()) {
            return result(
                    attempt,
                    answer,
                    snapshot,
                    selectedKey,
                    answeredCount,
                    null
            );
        }

        int correctCount = quizAttemptMapper.countCorrectByAttemptId(
                attempt.getAttemptId()
        );
        if (correctCount < 0 || correctCount > attempt.getTotalCount()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        LocalDateTime completedAt = answer.getAnsweredAt();
        QuizRewardResult reward = quizRewardService
                .grantForCompletedAttempt(
                        attempt.getUserId(),
                        attempt.getAttemptId(),
                        attempt.getAttemptNo(),
                        correctCount,
                        completedAt
                );

        attempt.setStatus(QuizAttemptStatus.GRADED);
        attempt.setCorrectCount(correctCount);
        attempt.setScore(score(correctCount, attempt.getTotalCount()));
        attempt.setRewardPolicyId(reward.policyId());
        attempt.setPointTransactionId(reward.pointTransactionId());
        attempt.setSubmittedAt(completedAt);

        if (quizAttemptMapper.completeAttemptIfInProgress(attempt) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return result(
                attempt,
                answer,
                snapshot,
                selectedKey,
                answeredCount,
                reward
        );
    }

    private QuizAnswerGradingResult result(
            QuizAttempt attempt,
            QuizAnswer answer,
            QuestionSnapshot snapshot,
            String selectedKey
    ) {
        int answeredCount = answeredCount(attempt);
        QuizRewardResult reward = attempt.getStatus() == QuizAttemptStatus.GRADED
                ? quizRewardService.restore(
                        attempt.getUserId(),
                        attempt.getAttemptId(),
                        attempt.getRewardPolicyId(),
                        attempt.getPointTransactionId()
                )
                : null;
        return result(
                attempt,
                answer,
                snapshot,
                selectedKey,
                answeredCount,
                reward
        );
    }

    private QuizAnswerGradingResult result(
            QuizAttempt attempt,
            QuizAnswer answer,
            QuestionSnapshot snapshot,
            String selectedKey,
            int answeredCount,
            QuizRewardResult reward
    ) {
        if (answer.getCorrect() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        boolean completed = attempt.getStatus() == QuizAttemptStatus.GRADED;

        return new QuizAnswerGradingResult(
                attempt.getAttemptId(),
                answer.getQuestionId(),
                snapshot.generationType(),
                selectedKey,
                answer.getCorrect(),
                snapshot.correctKey(),
                snapshot.explanation(),
                attempt.getStatus(),
                answeredCount,
                attempt.getTotalCount(),
                answeredCount == attempt.getTotalCount(),
                completed ? attempt.getCorrectCount() : null,
                completed ? attempt.getScore() : null,
                reward,
                completed && attempt.getQuizType() == org.firstfolio.quiz.domain.QuizType.SUB_CHAPTER
                        ? "NEXT_SUB_CHAPTER"
                        : null
        );
    }

    private int answeredCount(QuizAttempt attempt) {
        int answeredCount = quizAttemptMapper.countAnsweredByAttemptId(
                attempt.getAttemptId()
        );
        if (answeredCount < 0 || answeredCount > attempt.getTotalCount()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return answeredCount;
    }

    private int score(int correctCount, int totalCount) {
        if (totalCount <= 0) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return Math.round(correctCount * 100.0f / totalCount);
    }

    private String normalizeSelectedKey(String selectedKey) {
        if (selectedKey == null) {
            throw new ApiException(ErrorCode.INVALID_SELECTED_CHOICE);
        }

        String normalized = selectedKey.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_CHOICE_KEY_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_SELECTED_CHOICE);
        }
        return normalized;
    }

    private QuestionSnapshot parseSnapshot(String snapshotJson) {
        try {
            JsonNode snapshot = objectMapper.readTree(snapshotJson);
            QuizGenerationType generationType = QuizGenerationType.valueOf(
                    requiredText(snapshot, "generation_type")
            );

            JsonNode options = snapshot.path("options_json");
            if (!options.isArray() || options.isEmpty()) {
                throw internalError(null);
            }
            Set<String> optionKeys = new HashSet<>();
            for (JsonNode option : options) {
                if (!optionKeys.add(requiredText(option, "key"))) {
                    throw internalError(null);
                }
            }

            String correctKey = requiredText(
                    snapshot.path("correct_answer_json"),
                    "key"
            );
            if (!optionKeys.contains(correctKey)) {
                throw internalError(null);
            }

            return new QuestionSnapshot(
                    generationType,
                    Set.copyOf(optionKeys),
                    correctKey,
                    requiredText(snapshot, "explanation")
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw internalError(exception);
        }
    }

    private String parseUserAnswer(String userAnswerJson) {
        try {
            return requiredText(objectMapper.readTree(userAnswerJson), "key");
        } catch (JsonProcessingException exception) {
            throw internalError(exception);
        }
    }

    private String toUserAnswerJson(String selectedKey) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("key", selectedKey);
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException exception) {
            throw internalError(exception);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        if (node == null) {
            throw internalError(null);
        }
        String value = node.path(fieldName).textValue();
        if (value == null || value.isBlank()) {
            throw internalError(null);
        }
        return value;
    }

    private ApiException internalError(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                cause
        );
    }

    private record QuestionSnapshot(
            QuizGenerationType generationType,
            Set<String> optionKeys,
            String correctKey,
            String explanation
    ) {
    }
}
