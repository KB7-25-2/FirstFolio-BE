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
    private final ObjectMapper objectMapper;

    public QuizAnswerGradingService(
            QuizAttemptMapper quizAttemptMapper,
            Clock clock
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.clock = clock;
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
        return result(attempt, answer, snapshot, selectedKey);
    }

    private QuizAnswerGradingResult result(
            QuizAttempt attempt,
            QuizAnswer answer,
            QuestionSnapshot snapshot,
            String selectedKey
    ) {
        if (answer.getCorrect() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        int answeredCount = quizAttemptMapper.countAnsweredByAttemptId(
                attempt.getAttemptId()
        );
        if (answeredCount < 0 || answeredCount > attempt.getTotalCount()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

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
                answeredCount == attempt.getTotalCount()
        );
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
