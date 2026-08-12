package org.firstfolio.quiz.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 퀴즈 문항 원본 JSON의 구조와 JSON 내부 일관성을 검증한다.
 *
 * <p>대단원·소단원 존재 여부와 AI 근거 콘텐츠 ID 존재 여부 같은 DB 연계 규칙은
 * 이 검증기의 범위가 아니다.</p>
 */
@Component
public final class QuizQuestionSchemaValidator {

    private static final String SCHEMA_RESOURCE =
            "schemas/quiz/1.0/quiz-question.schema.json";

    private final ObjectMapper objectMapper;
    private final Schema schema;

    public QuizQuestionSchemaValidator() {
        this(new ObjectMapper());
    }

    QuizQuestionSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.schema = loadSchema();
    }

    public QuizQuestionValidationResult validate(byte[] content) {
        if (content == null || content.length == 0) {
            return invalidJson("퀴즈 문항 JSON이 비어 있습니다.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            return invalidJson("올바른 JSON 형식이 아닙니다.");
        } catch (IOException exception) {
            return invalidJson("퀴즈 문항 JSON을 읽을 수 없습니다.");
        }

        if (root == null) {
            return invalidJson("퀴즈 문항 JSON이 비어 있습니다.");
        }

        List<QuizQuestionValidationError> errors = new ArrayList<>();
        for (Error error : schema.validate(root.toString(), InputFormat.JSON)) {
            errors.add(new QuizQuestionValidationError(
                    QuizQuestionValidationErrorCode.SCHEMA_VIOLATION,
                    error.getInstanceLocation().toString(),
                    error.getMessage()
            ));
        }

        if (errors.isEmpty()) {
            errors.addAll(validateOptionKeys(root));
        }

        errors.sort(Comparator
                .comparing(QuizQuestionValidationError::path)
                .thenComparing(error -> error.code().name())
                .thenComparing(QuizQuestionValidationError::message));

        return errors.isEmpty()
                ? QuizQuestionValidationResult.valid()
                : new QuizQuestionValidationResult(errors);
    }

    private List<QuizQuestionValidationError> validateOptionKeys(JsonNode root) {
        JsonNode options = root.path("options_json");
        Set<String> seenKeys = new HashSet<>();
        Map<String, Integer> firstIndexes = new HashMap<>();
        List<QuizQuestionValidationError> errors = new ArrayList<>();

        for (int index = 0; index < options.size(); index++) {
            String key = options.get(index).path("key").textValue();
            if (seenKeys.add(key)) {
                firstIndexes.put(key, index);
                continue;
            }

            errors.add(new QuizQuestionValidationError(
                    QuizQuestionValidationErrorCode.DUPLICATE_OPTION_KEY,
                    "/options_json/" + index + "/key",
                    "선택지 key '" + key + "'가 /options_json/"
                            + firstIndexes.get(key) + "/key와 중복됩니다."
            ));
        }

        String correctKey = root.path("correct_answer_json").path("key").textValue();
        if (!seenKeys.contains(correctKey)) {
            errors.add(new QuizQuestionValidationError(
                    QuizQuestionValidationErrorCode.CORRECT_OPTION_NOT_FOUND,
                    "/correct_answer_json/key",
                    "정답 key '" + correctKey + "'가 options_json에 존재하지 않습니다."
            ));
        }
        return errors;
    }

    private Schema loadSchema() {
        ClassLoader classLoader = QuizQuestionSchemaValidator.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "퀴즈 문항 JSON Schema를 찾을 수 없습니다: " + SCHEMA_RESOURCE
                );
            }

            String schemaData = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
            return registry.getSchema(schemaData, InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "퀴즈 문항 JSON Schema를 읽을 수 없습니다: " + SCHEMA_RESOURCE,
                    exception
            );
        }
    }

    private QuizQuestionValidationResult invalidJson(String message) {
        return QuizQuestionValidationResult.invalid(new QuizQuestionValidationError(
                QuizQuestionValidationErrorCode.INVALID_JSON,
                "",
                message
        ));
    }
}
