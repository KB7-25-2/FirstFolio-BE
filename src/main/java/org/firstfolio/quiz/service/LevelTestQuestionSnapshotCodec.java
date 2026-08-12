package org.firstfolio.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAttemptQuestion;
import org.firstfolio.quiz.domain.LevelTestSavedAnswer;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizChoice;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LevelTestQuestionSnapshotCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    String createSnapshot(QuizQuestion question, MainChapter chapter) {
        if (question.getQuestionId() == null
                || question.getMainChapterId() == null
                || chapter.getMainChapterId() == null
                || !question.getMainChapterId().equals(chapter.getMainChapterId())
                || chapter.getAssetType() == null
                || question.getQuestionType() == null
                || question.getGenerationType() == null) {
            throw invalidQuestionSet(null);
        }

        JsonNode scenario = parseNullableJson(question.getScenarioJson());
        JsonNode options = parseRequiredJson(question.getOptionsJson());
        JsonNode correctAnswer = parseRequiredJson(
                question.getCorrectAnswerJson()
        );
        validateAnswerSchema(options, correctAnswer);

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("main_chapter_id", chapter.getMainChapterId());
        snapshot.put("asset_type", chapter.getAssetType().name());
        snapshot.put("question_type", question.getQuestionType().name());
        snapshot.put("generation_type", question.getGenerationType().name());
        snapshot.put("prompt", requireText(question.getPrompt()));
        snapshot.set("scenario_json", scenario);
        snapshot.set("options_json", options);
        snapshot.set("correct_answer_json", correctAnswer);
        snapshot.put("explanation", requireText(question.getExplanation()));

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw invalidQuestionSet(exception);
        }
    }

    LevelTestAttemptQuestion toQuestionView(QuizAnswer answer) {
        ParsedSnapshot snapshot = parseSnapshot(answer.getQuestionSnapshotJson());
        return new LevelTestAttemptQuestion(
                answer.getQuestionId(),
                answer.getDisplayOrder(),
                snapshot.mainChapterId(),
                snapshot.assetType(),
                snapshot.questionType(),
                snapshot.generationType(),
                snapshot.prompt(),
                snapshot.scenario(),
                snapshot.choices()
        );
    }

    LevelTestSavedAnswer toSavedAnswer(QuizAnswer answer) {
        if (answer.getUserAnswerJson() == null) {
            return null;
        }
        ParsedSnapshot snapshot = parseSnapshot(answer.getQuestionSnapshotJson());
        try {
            String key = requiredText(
                    objectMapper.readTree(answer.getUserAnswerJson()),
                    "key"
            );
            if (!snapshot.choiceKeys().contains(key)) {
                throw invalidQuestionSet(null);
            }
            return new LevelTestSavedAnswer(answer.getQuestionId(), key);
        } catch (JsonProcessingException exception) {
            throw invalidQuestionSet(exception);
        }
    }

    private ParsedSnapshot parseSnapshot(String snapshotJson) {
        try {
            JsonNode snapshot = objectMapper.readTree(snapshotJson);
            long mainChapterId = requiredPositiveLong(
                    snapshot,
                    "main_chapter_id"
            );
            AssetType assetType = AssetType.valueOf(
                    requiredText(snapshot, "asset_type")
            );
            QuizQuestionType questionType = QuizQuestionType.valueOf(
                    requiredText(snapshot, "question_type")
            );
            QuizGenerationType generationType = QuizGenerationType.valueOf(
                    requiredText(snapshot, "generation_type")
            );
            String prompt = requiredText(snapshot, "prompt");
            JsonNode scenario = snapshot.get("scenario_json");
            if (scenario == null) {
                throw invalidQuestionSet(null);
            }
            if (scenario.isNull()) {
                scenario = null;
            }

            JsonNode options = snapshot.path("options_json");
            JsonNode correctAnswer = snapshot.path("correct_answer_json");
            Set<String> choiceKeys = validateAnswerSchema(
                    options,
                    correctAnswer
            );
            List<QuizChoice> choices = new ArrayList<>();
            for (JsonNode option : options) {
                choices.add(new QuizChoice(
                        requiredText(option, "key"),
                        requiredText(option, "label")
                ));
            }
            requiredText(snapshot, "explanation");

            return new ParsedSnapshot(
                    mainChapterId,
                    assetType,
                    questionType,
                    generationType,
                    prompt,
                    scenario,
                    List.copyOf(choices),
                    choiceKeys
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidQuestionSet(exception);
        }
    }

    private Set<String> validateAnswerSchema(
            JsonNode options,
            JsonNode correctAnswer
    ) {
        if (!options.isArray() || options.isEmpty()) {
            throw invalidQuestionSet(null);
        }
        Set<String> keys = new HashSet<>();
        for (JsonNode option : options) {
            String key = requiredText(option, "key");
            requiredText(option, "label");
            if (!keys.add(key)) {
                throw invalidQuestionSet(null);
            }
        }
        if (!keys.contains(requiredText(correctAnswer, "key"))) {
            throw invalidQuestionSet(null);
        }
        return Set.copyOf(keys);
    }

    private JsonNode parseNullableJson(String json) {
        return json == null ? objectMapper.nullNode() : parseRequiredJson(json);
    }

    private JsonNode parseRequiredJson(String json) {
        if (json == null) {
            throw invalidQuestionSet(null);
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed == null) {
                throw invalidQuestionSet(null);
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw invalidQuestionSet(exception);
        }
    }

    private long requiredPositiveLong(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw invalidQuestionSet(null);
        }
        return value.longValue();
    }

    private String requiredText(JsonNode node, String fieldName) {
        return requireText(node.path(fieldName).textValue());
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw invalidQuestionSet(null);
        }
        return value;
    }

    private ApiException invalidQuestionSet(Throwable cause) {
        return new ApiException(
                ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID,
                ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID.getDefaultMessage(),
                cause
        );
    }

    private record ParsedSnapshot(
            long mainChapterId,
            AssetType assetType,
            QuizQuestionType questionType,
            QuizGenerationType generationType,
            String prompt,
            JsonNode scenario,
            List<QuizChoice> choices,
            Set<String> choiceKeys
    ) {
        private ParsedSnapshot {
            scenario = scenario == null ? null : scenario.deepCopy();
            choices = List.copyOf(choices);
            choiceKeys = Set.copyOf(choiceKeys);
        }

        @Override
        public JsonNode scenario() {
            return scenario == null ? null : scenario.deepCopy();
        }
    }
}
