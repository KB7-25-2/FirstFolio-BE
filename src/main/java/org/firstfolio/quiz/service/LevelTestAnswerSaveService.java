package org.firstfolio.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveCommand;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveResult;
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
import java.util.List;
import java.util.Set;

@Service
public class LevelTestAnswerSaveService {

    private static final int MAX_CHOICE_KEY_LENGTH = 50;

    private final QuizAttemptMapper quizAttemptMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final LevelTestQuestionSnapshotCodec snapshotCodec;

    public LevelTestAnswerSaveService(
            QuizAttemptMapper quizAttemptMapper,
            Clock clock
    ) {
        this.quizAttemptMapper = quizAttemptMapper;
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
        this.snapshotCodec = new LevelTestQuestionSnapshotCodec();
    }

    @Transactional
    public LevelTestAnswerSaveResult save(
            long userId,
            long attemptId,
            List<LevelTestAnswerSaveCommand> requestedAnswers
    ) {
        List<NormalizedAnswer> normalizedAnswers = normalize(requestedAnswers);

        QuizAttempt attempt = quizAttemptMapper.findByIdForUpdate(attemptId);
        if (attempt == null || attempt.getQuizType() != QuizType.LEVEL_TEST) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
        }
        if (attempt.getUserId() != userId) {
            throw new ApiException(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN);
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.ATTEMPT_ALREADY_GRADED);
        }
        if (attempt.getTotalCount() <= 0) {
            throw new ApiException(ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID);
        }

        LocalDateTime updatedAt = LocalDateTime.now(clock);
        List<QuizAnswer> answersToSave = new ArrayList<>();
        for (NormalizedAnswer requested : normalizedAnswers) {
            QuizAnswer answer = quizAttemptMapper
                    .findAnswerByAttemptIdAndQuestionIdForUpdate(
                            attemptId,
                            requested.questionId()
                    );
            if (answer == null) {
                throw new ApiException(ErrorCode.QUESTION_NOT_IN_ATTEMPT);
            }
            if (answer.getCorrect() != null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
            if (!snapshotCodec.containsChoiceKey(answer, requested.key())) {
                throw new ApiException(ErrorCode.INVALID_SELECTED_CHOICE);
            }

            answer.setUserAnswerJson(toUserAnswerJson(requested.key()));
            answer.setCorrect(null);
            answer.setAnsweredAt(updatedAt);
            answersToSave.add(answer);
        }

        for (QuizAnswer answer : answersToSave) {
            if (quizAttemptMapper.saveLevelTestAnswer(answer) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }

        int answeredCount = quizAttemptMapper.countAnsweredByAttemptId(
                attemptId
        );
        if (answeredCount < 0 || answeredCount > attempt.getTotalCount()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        return new LevelTestAnswerSaveResult(
                attemptId,
                answersToSave.size(),
                answeredCount,
                attempt.getTotalCount(),
                attempt.getStatus(),
                updatedAt
        );
    }

    private List<NormalizedAnswer> normalize(
            List<LevelTestAnswerSaveCommand> requestedAnswers
    ) {
        if (requestedAnswers == null || requestedAnswers.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        Set<Long> questionIds = new HashSet<>();
        List<NormalizedAnswer> normalized = new ArrayList<>();
        for (LevelTestAnswerSaveCommand requested : requestedAnswers) {
            if (requested == null
                    || requested.questionId() == null
                    || requested.questionId() <= 0
                    || !questionIds.add(requested.questionId())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST);
            }
            normalized.add(new NormalizedAnswer(
                    requested.questionId(),
                    normalizeKey(requested.selectedKey())
            ));
        }
        return List.copyOf(normalized);
    }

    private String normalizeKey(String selectedKey) {
        if (selectedKey == null) {
            throw new ApiException(ErrorCode.INVALID_SELECTED_CHOICE);
        }
        String normalized = selectedKey.strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CHOICE_KEY_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_SELECTED_CHOICE);
        }
        return normalized;
    }

    private String toUserAnswerJson(String selectedKey) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("key", selectedKey);
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                    exception
            );
        }
    }

    private record NormalizedAnswer(long questionId, String key) {
    }
}
