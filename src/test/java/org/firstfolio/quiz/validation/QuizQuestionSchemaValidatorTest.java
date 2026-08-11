package org.firstfolio.quiz.validation;

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

class QuizQuestionSchemaValidatorTest {

    private static final String HUMAN_SINGLE_CHOICE_RESOURCE =
            "quiz/question/valid-human-single-choice.json";
    private static final String AI_SCENARIO_RESOURCE =
            "quiz/question/valid-ai-scenario.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuizQuestionSchemaValidator validator = new QuizQuestionSchemaValidator();

    @Test
    void acceptsValidHumanSingleChoiceQuestion() throws IOException {
        QuizQuestionValidationResult result = validator.validate(
                resourceBytes(HUMAN_SINGLE_CHOICE_RESOURCE)
        );

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void acceptsValidAiScenarioQuestion() throws IOException {
        QuizQuestionValidationResult result = validator.validate(
                resourceBytes(AI_SCENARIO_RESOURCE)
        );

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void acceptsTrueFalseQuestionWithOAndXOptions() throws IOException {
        ObjectNode question = humanSingleChoice();
        question.put("question_type", "TRUE_FALSE");
        ArrayNode options = question.withArray("options_json");
        ((ObjectNode) options.get(0)).put("key", "O");
        ((ObjectNode) options.get(1)).put("key", "X");
        question.withObject("/correct_answer_json").put("key", "O");

        QuizQuestionValidationResult result = validate(question);

        assertTrue(result.isValid());
    }

    @Test
    void rejectsMalformedEmptyAndTrailingJson() throws IOException {
        QuizQuestionValidationResult malformed = validator.validate(
                "{broken".getBytes(StandardCharsets.UTF_8)
        );
        QuizQuestionValidationResult nullContent = validator.validate(null);
        QuizQuestionValidationResult empty = validator.validate(new byte[0]);
        QuizQuestionValidationResult trailing = validator.validate(
                (new String(resourceBytes(HUMAN_SINGLE_CHOICE_RESOURCE), StandardCharsets.UTF_8)
                        + " {}")
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertInvalidJson(malformed);
        assertInvalidJson(nullContent);
        assertInvalidJson(empty);
        assertInvalidJson(trailing);
    }

    @Test
    void rejectsUnknownFieldAndRemovedMultipleChoiceType() throws IOException {
        ObjectNode unknownField = humanSingleChoice();
        unknownField.put("answer", "1");
        ObjectNode multipleChoice = humanSingleChoice();
        multipleChoice.put("question_type", "MULTIPLE_CHOICE");

        assertSchemaViolation(validate(unknownField));
        assertSchemaViolation(validate(multipleChoice));
    }

    @Test
    void rejectsDuplicateOptionKeys() throws IOException {
        ObjectNode question = humanSingleChoice();
        ((ObjectNode) question.withArray("options_json").get(1)).put("key", "1");

        QuizQuestionValidationResult result = validate(question);

        assertFalse(result.isValid());
        assertEquals(QuizQuestionValidationErrorCode.DUPLICATE_OPTION_KEY,
                result.errors().get(0).code());
        assertEquals("/options_json/1/key", result.errors().get(0).path());
    }

    @Test
    void rejectsCorrectAnswerKeyMissingFromOptions() throws IOException {
        ObjectNode question = humanSingleChoice();
        question.withObject("/correct_answer_json").put("key", "3");

        QuizQuestionValidationResult result = validate(question);

        assertFalse(result.isValid());
        assertEquals(QuizQuestionValidationErrorCode.CORRECT_OPTION_NOT_FOUND,
                result.errors().get(0).code());
        assertEquals("/correct_answer_json/key", result.errors().get(0).path());
    }

    @Test
    void rejectsTrueFalseQuestionWithoutExactOAndXOptions() throws IOException {
        ObjectNode question = humanSingleChoice();
        question.put("question_type", "TRUE_FALSE");
        ArrayNode options = question.withArray("options_json");
        ((ObjectNode) options.get(0)).put("key", "O");
        ((ObjectNode) options.get(1)).put("key", "Y");
        question.withObject("/correct_answer_json").put("key", "O");

        assertSchemaViolation(validate(question));
    }

    @Test
    void rejectsScenarioPayloadThatDoesNotMatchQuestionType() throws IOException {
        ObjectNode missingScenario = humanSingleChoice();
        missingScenario.put("question_type", "SCENARIO");
        ObjectNode unexpectedScenario = aiScenario();
        unexpectedScenario.put("question_type", "SINGLE_CHOICE");

        assertSchemaViolation(validate(missingScenario));
        assertSchemaViolation(validate(unexpectedScenario));
    }

    @Test
    void rejectsSourceReferencesThatDoNotMatchGenerationType() throws IOException {
        ObjectNode aiWithoutSources = humanSingleChoice();
        aiWithoutSources.put("generation_type", "AI");
        ObjectNode humanWithSources = aiScenario();
        humanWithSources.put("generation_type", "HUMAN");

        assertSchemaViolation(validate(aiWithoutSources));
        assertSchemaViolation(validate(humanWithSources));
    }

    @Test
    void rejectsAiSourceWithoutKnowledgeContentIdOrUrl() throws IOException {
        ObjectNode question = aiScenario();
        ObjectNode source = (ObjectNode) question.withArray("source_refs_json").get(0);
        source.remove("knowledge_content_id");

        assertSchemaViolation(validate(question));
    }

    @Test
    void rejectsChapterReferencesThatDoNotMatchUsageType() throws IOException {
        ObjectNode subChapterWithoutId = humanSingleChoice();
        subChapterWithoutId.putNull("sub_chapter_id");
        ObjectNode mainChapterWithSubChapterId = aiScenario();
        mainChapterWithSubChapterId.put("sub_chapter_id", 101);

        assertSchemaViolation(validate(subChapterWithoutId));
        assertSchemaViolation(validate(mainChapterWithSubChapterId));
    }

    @Test
    void rejectsHtmlInQuestionText() throws IOException {
        ObjectNode question = humanSingleChoice();
        question.put("prompt", "예금은 <strong>안전한가요?</strong>");

        assertSchemaViolation(validate(question));
    }

    private ObjectNode humanSingleChoice() throws IOException {
        return resourceObject(HUMAN_SINGLE_CHOICE_RESOURCE);
    }

    private ObjectNode aiScenario() throws IOException {
        return resourceObject(AI_SCENARIO_RESOURCE);
    }

    private ObjectNode resourceObject(String resource) throws IOException {
        return (ObjectNode) objectMapper.readTree(resourceBytes(resource));
    }

    private byte[] resourceBytes(String resource) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IllegalStateException("테스트 퀴즈 문항 JSON을 찾을 수 없습니다: " + resource);
            }
            return inputStream.readAllBytes();
        }
    }

    private QuizQuestionValidationResult validate(ObjectNode question) throws IOException {
        return validator.validate(objectMapper.writeValueAsBytes(question));
    }

    private void assertInvalidJson(QuizQuestionValidationResult result) {
        assertFalse(result.isValid());
        assertEquals(QuizQuestionValidationErrorCode.INVALID_JSON,
                result.errors().get(0).code());
    }

    private void assertSchemaViolation(QuizQuestionValidationResult result) {
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.code()
                        == QuizQuestionValidationErrorCode.SCHEMA_VIOLATION));
    }
}
