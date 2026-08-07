package org.firstfolio.content.validation;

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
 * 소단원 강좌 JSON의 구조와 JSON 내부 일관성을 검증한다.
 *
 * <p>문항의 존재 여부, 공개 상태, 소단원 소속과 같은 DB 연계 규칙은 이 검증기의 범위가 아니다.</p>
 */
@Component
public final class LessonSchemaValidator {

    private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String VERSION_1_0 = "1.0";
    private static final String VERSION_1_0_RESOURCE =
            "schemas/lesson/1.0/lesson.schema.json";

    private final ObjectMapper objectMapper;
    private final Map<String, Schema> schemas;

    public LessonSchemaValidator() {
        this(new ObjectMapper());
    }

    LessonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.schemas = Map.of(VERSION_1_0, loadSchema(VERSION_1_0_RESOURCE));
    }

    public LessonValidationResult validate(byte[] content) {
        if (content == null || content.length == 0) {
            return invalidJson("강좌 JSON이 비어 있습니다.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            return invalidJson("올바른 JSON 형식이 아닙니다.");
        } catch (IOException exception) {
            return invalidJson("강좌 JSON을 읽을 수 없습니다.");
        }

        if (root == null) {
            return invalidJson("강좌 JSON이 비어 있습니다.");
        }

        JsonNode versionNode = root.get(SCHEMA_VERSION_FIELD);
        if (versionNode == null || !versionNode.isTextual() || versionNode.textValue().isBlank()) {
            return LessonValidationResult.invalid(new LessonValidationError(
                    LessonValidationErrorCode.SCHEMA_VERSION_REQUIRED,
                    "/schemaVersion",
                    "schemaVersion은 공백이 아닌 문자열로 입력해야 합니다."
            ));
        }

        String schemaVersion = versionNode.textValue();
        Schema schema = schemas.get(schemaVersion);
        if (schema == null) {
            return LessonValidationResult.invalid(new LessonValidationError(
                    LessonValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                    "/schemaVersion",
                    "지원하지 않는 강좌 스키마 버전입니다: " + schemaVersion
            ));
        }

        List<LessonValidationError> errors = new ArrayList<>();
        for (Error error : schema.validate(root.toString(), InputFormat.JSON)) {
            errors.add(new LessonValidationError(
                    LessonValidationErrorCode.SCHEMA_VIOLATION,
                    error.getInstanceLocation().toString(),
                    error.getMessage()
            ));
        }

        if (errors.isEmpty()) {
            errors.addAll(findDuplicatePageIds(root));
        }

        errors.sort(Comparator
                .comparing(LessonValidationError::path)
                .thenComparing(error -> error.code().name())
                .thenComparing(LessonValidationError::message));

        return errors.isEmpty()
                ? LessonValidationResult.valid()
                : new LessonValidationResult(errors);
    }

    private List<LessonValidationError> findDuplicatePageIds(JsonNode root) {
        Set<String> seenPageIds = new HashSet<>();
        Map<String, Integer> firstIndexes = new HashMap<>();
        List<LessonValidationError> errors = new ArrayList<>();
        JsonNode pages = root.path("pages");

        for (int index = 0; index < pages.size(); index++) {
            String pageId = pages.get(index).path("id").textValue();
            if (seenPageIds.add(pageId)) {
                firstIndexes.put(pageId, index);
                continue;
            }

            errors.add(new LessonValidationError(
                    LessonValidationErrorCode.DUPLICATE_PAGE_ID,
                    "/pages/" + index + "/id",
                    "페이지 id '" + pageId + "'가 /pages/"
                            + firstIndexes.get(pageId) + "/id와 중복됩니다."
            ));
        }
        return errors;
    }

    private Schema loadSchema(String resourcePath) {
        ClassLoader classLoader = LessonSchemaValidator.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("강좌 JSON Schema를 찾을 수 없습니다: " + resourcePath);
            }

            String schemaData = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
            return registry.getSchema(schemaData, InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalStateException("강좌 JSON Schema를 읽을 수 없습니다: " + resourcePath, exception);
        }
    }

    private LessonValidationResult invalidJson(String message) {
        return LessonValidationResult.invalid(new LessonValidationError(
                LessonValidationErrorCode.INVALID_JSON,
                "",
                message
        ));
    }
}
