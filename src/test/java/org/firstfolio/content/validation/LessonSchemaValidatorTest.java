package org.firstfolio.content.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonSchemaValidatorTest {

    private static final String VALID_LESSON_RESOURCE =
            "content/lesson/valid-lesson-1.0.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LessonSchemaValidator validator = new LessonSchemaValidator();

    @Test
    void acceptsLessonThatMatchesVersionOneSchema() throws IOException {
        LessonValidationResult result = validator.validate(validLessonBytes());

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void acceptsOmittedOptionalFields() throws IOException {
        ObjectNode lesson = validLesson();
        ObjectNode conclusion = blockAt(lesson, 0, 1);
        conclusion.remove("note");
        ObjectNode learnMore = blockAt(lesson, 0, 3);
        learnMore.remove("chipLabel");
        learnMore.remove("chipSubtitle");
        ObjectNode modal = (ObjectNode) learnMore.get("modal");
        modal.remove("example");
        modal.remove("footer");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertTrue(result.isValid());
    }

    @Test
    void rejectsMalformedJson() {
        LessonValidationResult result = validator.validate(
                "{broken".getBytes(StandardCharsets.UTF_8)
        );

        assertFalse(result.isValid());
        assertEquals(LessonValidationErrorCode.INVALID_JSON, result.errors().get(0).code());
    }

    @Test
    void rejectsNullAndEmptyContent() {
        LessonValidationResult nullResult = validator.validate(null);
        LessonValidationResult emptyResult = validator.validate(new byte[0]);

        assertEquals(LessonValidationErrorCode.INVALID_JSON, nullResult.errors().get(0).code());
        assertEquals(LessonValidationErrorCode.INVALID_JSON, emptyResult.errors().get(0).code());
    }

    @Test
    void rejectsTrailingJsonContent() throws IOException {
        byte[] validLesson = validLessonBytes();
        byte[] trailingJson = (new String(validLesson, StandardCharsets.UTF_8) + " {}")
                .getBytes(StandardCharsets.UTF_8);

        LessonValidationResult result = validator.validate(trailingJson);

        assertFalse(result.isValid());
        assertEquals(LessonValidationErrorCode.INVALID_JSON, result.errors().get(0).code());
    }

    @Test
    void rejectsMissingSchemaVersion() throws IOException {
        ObjectNode lesson = validLesson();
        lesson.remove("schemaVersion");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertFalse(result.isValid());
        assertEquals(
                LessonValidationErrorCode.SCHEMA_VERSION_REQUIRED,
                result.errors().get(0).code()
        );
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws IOException {
        ObjectNode lesson = validLesson();
        lesson.put("schemaVersion", "2.0");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertFalse(result.isValid());
        assertEquals(
                LessonValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                result.errors().get(0).code()
        );
    }

    @Test
    void rejectsUnknownRootAndBlockFields() throws IOException {
        ObjectNode lesson = validLesson();
        lesson.put("subChapterId", 103);
        blockAt(lesson, 0, 0).put("html", "<strong>금리</strong>");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertSchemaViolation(result);
    }

    @Test
    void rejectsUnknownBlockType() throws IOException {
        ObjectNode lesson = validLesson();
        blockAt(lesson, 0, 0).put("type", "image");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertSchemaViolation(result);
    }

    @Test
    void rejectsBlankRequiredAndPresentOptionalFields() throws IOException {
        ObjectNode lesson = validLesson();
        blockAt(lesson, 0, 0).put("content", " \n\t");
        blockAt(lesson, 0, 1).put("note", "  ");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertSchemaViolation(result);
    }

    @Test
    void rejectsHtmlInTextFields() throws IOException {
        ObjectNode lesson = validLesson();
        blockAt(lesson, 0, 0).put("content", "금리는 <strong>중요합니다</strong>.");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertSchemaViolation(result);
    }

    @Test
    void rejectsEmptyPagesAndBlocks() throws IOException {
        ObjectNode lessonWithNoPages = validLesson();
        lessonWithNoPages.putArray("pages");
        ObjectNode lessonWithNoBlocks = validLesson();
        ((ObjectNode) lessonWithNoBlocks.withArray("pages").get(0)).putArray("blocks");

        assertSchemaViolation(validator.validate(objectMapper.writeValueAsBytes(lessonWithNoPages)));
        assertSchemaViolation(validator.validate(objectMapper.writeValueAsBytes(lessonWithNoBlocks)));
    }

    @Test
    void rejectsInvalidAndDuplicateQuestionIds() throws IOException {
        ObjectNode lesson = validLesson();
        ArrayNode questionIds = lesson.withObject("/subChapterQuiz").putArray("questionIds");
        questionIds.add(1021).add(0).add(1021);

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertSchemaViolation(result);
    }

    @Test
    void rejectsDuplicatePageIds() throws IOException {
        ObjectNode lesson = validLesson();
        ArrayNode pages = lesson.withArray("pages");
        ObjectNode duplicate = pages.get(0).deepCopy();
        pages.add(duplicate);

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertFalse(result.isValid());
        LessonValidationError error = result.errors().get(0);
        assertEquals(LessonValidationErrorCode.DUPLICATE_PAGE_ID, error.code());
        assertEquals("/pages/2/id", error.path());
    }

    @Test
    void rejectsInvalidPageId() throws IOException {
        ObjectNode lesson = validLesson();
        ((ObjectNode) lesson.withArray("pages").get(0)).put("id", "Page Final");

        LessonValidationResult result = validator.validate(objectMapper.writeValueAsBytes(lesson));

        assertSchemaViolation(result);
    }

    private ObjectNode validLesson() throws IOException {
        return (ObjectNode) objectMapper.readTree(validLessonBytes());
    }

    private byte[] validLessonBytes() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(VALID_LESSON_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("테스트 강좌 JSON을 찾을 수 없습니다.");
            }
            return inputStream.readAllBytes();
        }
    }

    private ObjectNode blockAt(ObjectNode lesson, int pageIndex, int blockIndex) {
        JsonNode page = lesson.withArray("pages").get(pageIndex);
        return (ObjectNode) page.withArray("blocks").get(blockIndex);
    }

    private void assertSchemaViolation(LessonValidationResult result) {
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.code() == LessonValidationErrorCode.SCHEMA_VIOLATION));
    }
}
