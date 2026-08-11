package org.firstfolio.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.domain.QuizChoice;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionType;

import java.util.ArrayList;
import java.util.List;

final class QuizQuestionSnapshotCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    String createSnapshot(QuizQuestion question) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("question_type", question.getQuestionType().name());
        snapshot.put("generation_type", question.getGenerationType().name());
        snapshot.put("prompt", question.getPrompt());
        snapshot.set("scenario_json", parseNullableJson(question.getScenarioJson()));
        snapshot.set("options_json", parseRequiredJson(question.getOptionsJson()));
        snapshot.set(
                "correct_answer_json",
                parseRequiredJson(question.getCorrectAnswerJson())
        );
        snapshot.put("explanation", question.getExplanation());

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    QuizAttemptQuestion toQuestionView(QuizAnswer answer) {
        try {
            JsonNode snapshot = objectMapper.readTree(
                    answer.getQuestionSnapshotJson()
            );
            QuizQuestionType questionType = QuizQuestionType.valueOf(
                    requiredText(snapshot, "question_type")
            );
            QuizGenerationType generationType = QuizGenerationType.valueOf(
                    requiredText(snapshot, "generation_type")
            );
            String prompt = requiredText(snapshot, "prompt");
            JsonNode scenario = snapshot.get("scenario_json");
            if (scenario == null || scenario.isMissingNode()) {
                throw unavailable(null);
            }
            if (scenario.isNull()) {
                scenario = null;
            }

            JsonNode options = snapshot.path("options_json");
            if (!options.isArray() || options.isEmpty()) {
                throw unavailable(null);
            }
            List<QuizChoice> choices = new ArrayList<>();
            for (JsonNode option : options) {
                choices.add(new QuizChoice(
                        requiredText(option, "key"),
                        requiredText(option, "label")
                ));
            }

            return new QuizAttemptQuestion(
                    answer.getQuestionId(),
                    answer.getDisplayOrder(),
                    questionType,
                    generationType,
                    prompt,
                    scenario,
                    choices
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    private JsonNode parseNullableJson(String json) {
        return json == null ? objectMapper.nullNode() : parseRequiredJson(json);
    }

    private JsonNode parseRequiredJson(String json) {
        if (json == null) {
            throw unavailable(null);
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed == null) {
                throw unavailable(null);
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).textValue();
        if (value == null || value.isBlank()) {
            throw unavailable(null);
        }
        return value;
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(
                ErrorCode.CONTENT_UNAVAILABLE,
                ErrorCode.CONTENT_UNAVAILABLE.getDefaultMessage(),
                cause
        );
    }
}
