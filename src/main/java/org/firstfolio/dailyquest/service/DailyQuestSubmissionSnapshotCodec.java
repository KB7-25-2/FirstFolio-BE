package org.firstfolio.dailyquest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestItemGradingResult;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizGenerationType;

import java.util.HashSet;
import java.util.Set;

final class DailyQuestSubmissionSnapshotCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    DailyQuestItemGradingResult grade(DailyQuestItem item) {
        try {
            if (item == null
                    || item.getDailyQuestItemId() == null
                    || item.getDailyQuestItemId() <= 0
                    || item.getQuestionId() <= 0) {
                throw invalidSnapshot(null);
            }

            JsonNode snapshot = objectMapper.readTree(
                    item.getQuestionSnapshotJson()
            );
            if (snapshot == null || !snapshot.isObject()) {
                throw invalidSnapshot(null);
            }
            QuizGenerationType generationType = QuizGenerationType.valueOf(
                    requiredText(snapshot, "generation_type")
            );
            Set<String> optionKeys = optionKeys(snapshot.path("options_json"));
            String correctKey = requiredText(
                    snapshot.path("correct_answer_json"),
                    "key"
            );
            String submittedKey = requiredText(
                    objectMapper.readTree(item.getUserAnswerJson()),
                    "key"
            );
            if (!optionKeys.contains(correctKey)
                    || !optionKeys.contains(submittedKey)) {
                throw invalidSnapshot(null);
            }

            return new DailyQuestItemGradingResult(
                    item.getDailyQuestItemId(),
                    item.getQuestionId(),
                    correctKey.equals(submittedKey),
                    submittedKey,
                    correctKey,
                    requiredText(snapshot, "explanation"),
                    sourceRefs(snapshot, generationType)
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidSnapshot(exception);
        }
    }

    private Set<String> optionKeys(JsonNode options) {
        if (!options.isArray() || options.isEmpty()) {
            throw invalidSnapshot(null);
        }
        Set<String> keys = new HashSet<>();
        for (JsonNode option : options) {
            String key = requiredText(option, "key");
            requiredText(option, "label");
            if (!keys.add(key)) {
                throw invalidSnapshot(null);
            }
        }
        return Set.copyOf(keys);
    }

    private JsonNode sourceRefs(
            JsonNode snapshot,
            QuizGenerationType generationType
    ) {
        JsonNode sourceRefs = snapshot.get("source_refs_json");
        if (sourceRefs == null) {
            throw invalidSnapshot(null);
        }
        if (generationType == QuizGenerationType.HUMAN) {
            if (!sourceRefs.isNull()) {
                throw invalidSnapshot(null);
            }
            return null;
        }
        if (sourceRefs.isNull()) {
            return null;
        }
        if (!sourceRefs.isArray() || sourceRefs.isEmpty()) {
            throw invalidSnapshot(null);
        }
        return sourceRefs.deepCopy();
    }

    private String requiredText(JsonNode node, String fieldName) {
        if (node == null) {
            throw invalidSnapshot(null);
        }
        String value = node.path(fieldName).textValue();
        if (value == null || value.isBlank()) {
            throw invalidSnapshot(null);
        }
        return value;
    }

    private ApiException invalidSnapshot(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                "저장된 일일 퀘스트 채점 데이터가 올바르지 않습니다.",
                cause
        );
    }
}
