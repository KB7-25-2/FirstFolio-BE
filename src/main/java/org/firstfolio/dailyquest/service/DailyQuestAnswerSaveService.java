package org.firstfolio.dailyquest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestAnswerSaveResult;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.mapper.DailyQuestMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.service.QuizQuestionSnapshotCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
public class DailyQuestAnswerSaveService {

    private static final int MAX_CHOICE_KEY_LENGTH = 50;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyQuestMapper dailyQuestMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final QuizQuestionSnapshotCodec snapshotCodec;

    public DailyQuestAnswerSaveService(
            DailyQuestMapper dailyQuestMapper,
            Clock clock
    ) {
        this.dailyQuestMapper = dailyQuestMapper;
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
        this.snapshotCodec = new QuizQuestionSnapshotCodec();
    }

    @Transactional
    public DailyQuestAnswerSaveResult save(
            long userId,
            long dailyQuestItemId,
            String requestedKey
    ) {
        Long dailyQuestId = dailyQuestMapper.findQuestIdByItemIdAndUserId(
                dailyQuestItemId,
                userId
        );
        if (dailyQuestId == null) {
            throw new ApiException(ErrorCode.DAILY_QUEST_ITEM_NOT_FOUND);
        }

        DailyQuest dailyQuest = dailyQuestMapper.findByIdForUpdate(
                dailyQuestId
        );
        requireTodaysOwnedQuest(dailyQuest, userId);
        if (dailyQuest.getStatus() == DailyQuestStatus.COMPLETED) {
            throw new ApiException(ErrorCode.DAILY_QUEST_ALREADY_COMPLETED);
        }

        DailyQuestItem item = dailyQuestMapper
                .findItemByIdAndUserIdForUpdate(
                        dailyQuestItemId,
                        userId
                );
        if (item == null || item.getDailyQuestId() != dailyQuestId) {
            throw new ApiException(ErrorCode.DAILY_QUEST_ITEM_NOT_FOUND);
        }

        String selectedKey = normalizeKey(requestedKey);
        QuizAttemptQuestion question = snapshotCodec.toQuestionView(
                item.getQuestionId(),
                item.getDisplayOrder(),
                item.getQuestionSnapshotJson()
        );
        if (question.choices().stream().noneMatch(
                choice -> choice.key().equals(selectedKey)
        )) {
            throw new ApiException(ErrorCode.INVALID_ANSWER);
        }

        String existingKey = item.getUserAnswerJson() == null
                ? null
                : parseStoredAnswer(item.getUserAnswerJson());
        if (!selectedKey.equals(existingKey)) {
            item.setUserAnswerJson(toStoredAnswer(selectedKey));
            item.setCorrect(null);
            item.setAnsweredAt(LocalDateTime.ofInstant(
                    clock.instant(),
                    ZoneOffset.UTC
            ));
            if (dailyQuestMapper.saveAnswer(item) != 1) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }

        if (dailyQuest.getStatus() == DailyQuestStatus.ASSIGNED
                && dailyQuestMapper.markInProgressIfAssigned(
                    dailyQuestId
                ) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        int totalCount = dailyQuestMapper.countItemsByDailyQuestId(
                dailyQuestId
        );
        int answeredCount = dailyQuestMapper
                .countAnsweredItemsByDailyQuestId(dailyQuestId);
        validateCounts(dailyQuest, totalCount, answeredCount);

        return new DailyQuestAnswerSaveResult(
                dailyQuestId,
                dailyQuestItemId,
                selectedKey,
                answeredCount,
                totalCount
        );
    }

    private void requireTodaysOwnedQuest(
            DailyQuest dailyQuest,
            long userId
    ) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SERVICE_ZONE);
        if (dailyQuest == null
                || dailyQuest.getDailyQuestId() == null
                || dailyQuest.getDailyQuestId() <= 0
                || dailyQuest.getUserId() != userId
                || !today.equals(dailyQuest.getQuestDate())
                || dailyQuest.getStatus() == null) {
            throw new ApiException(ErrorCode.DAILY_QUEST_ITEM_NOT_FOUND);
        }
    }

    private String normalizeKey(String requestedKey) {
        if (requestedKey == null) {
            throw new ApiException(ErrorCode.INVALID_ANSWER);
        }
        String normalized = requestedKey.strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CHOICE_KEY_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_ANSWER);
        }
        return normalized;
    }

    private String parseStoredAnswer(String userAnswerJson) {
        try {
            JsonNode answer = objectMapper.readTree(userAnswerJson);
            if (answer == null || !answer.isObject()) {
                throw internalError(null);
            }
            String key = answer.path("key").textValue();
            if (key == null || key.isBlank()) {
                throw internalError(null);
            }
            return key;
        } catch (JsonProcessingException exception) {
            throw internalError(exception);
        }
    }

    private String toStoredAnswer(String selectedKey) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("key", selectedKey);
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException exception) {
            throw internalError(exception);
        }
    }

    private void validateCounts(
            DailyQuest dailyQuest,
            int totalCount,
            int answeredCount
    ) {
        if (totalCount != DailyQuest.TOTAL_QUESTION_COUNT
                || totalCount != dailyQuest.getTotalCount()
                || answeredCount < 0
                || answeredCount > totalCount) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private ApiException internalError(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                cause
        );
    }
}
