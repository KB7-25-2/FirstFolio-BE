package org.firstfolio.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.dto.request.QuizQuestionCreateRequest;
import org.firstfolio.quiz.dto.request.QuizQuestionVersionCreateRequest;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.firstfolio.quiz.validation.QuizQuestionSchemaValidator;
import org.firstfolio.quiz.validation.QuizQuestionValidationError;
import org.firstfolio.quiz.validation.QuizQuestionValidationErrorCode;
import org.firstfolio.quiz.validation.QuizQuestionValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizQuestionRegistrationServiceTest {

    private static final long ACTOR_ID = 900L;
    private static final String REQUEST_ID = "req-quiz-create";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 6, 0);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private QuizQuestionSchemaValidator schemaValidator;
    private QuizQuestionMapper questionMapper;
    private MainChapterMapper mainChapterMapper;
    private SubChapterMapper subChapterMapper;
    private AdminAuditLogMapper auditLogMapper;
    private QuizQuestionRegistrationService service;

    @BeforeEach
    void setUp() {
        schemaValidator = mock(QuizQuestionSchemaValidator.class);
        questionMapper = mock(QuizQuestionMapper.class);
        mainChapterMapper = mock(MainChapterMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        auditLogMapper = mock(AdminAuditLogMapper.class);
        service = new QuizQuestionRegistrationService(
                schemaValidator,
                questionMapper,
                mainChapterMapper,
                subChapterMapper,
                auditLogMapper,
                Clock.fixed(Instant.parse("2026-08-10T06:00:00Z"), ZoneOffset.UTC)
        );
        when(schemaValidator.validate(any())).thenReturn(QuizQuestionValidationResult.valid());
        doAnswer(invocation -> {
            QuizQuestion question = invocation.getArgument(0);
            question.setQuestionId(1201L);
            return 1;
        }).when(questionMapper).insert(any(QuizQuestion.class));
    }

    @Test
    void createsFirstHumanDraftAndResolvesMainChapterFromSubChapter() throws Exception {
        SubChapter subChapter = subChapter(101L, 2L);
        MainChapter mainChapter = mainChapter(2L, ChapterType.ASSET);
        when(subChapterMapper.findById(101L)).thenReturn(subChapter);
        when(mainChapterMapper.findById(2L)).thenReturn(mainChapter);

        QuizQuestion created = service.createQuestion(
                subChapterRequest(),
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(1201L, created.getQuestionId());
        assertEquals("deposit-basic-001", created.getQuestionKey());
        assertEquals(1, created.getVersionNo());
        assertEquals(QuizGenerationType.HUMAN, created.getGenerationType());
        assertEquals(QuizQuestionStatus.DRAFT, created.getStatus());
        assertEquals(2L, created.getMainChapterId());
        assertEquals(101L, created.getSubChapterId());
        assertNull(created.getSourceRefsJson());
        assertNull(created.getPublishedAt());
        assertEquals(NOW, created.getCreatedAt());

        ArgumentCaptor<byte[]> validationContent = ArgumentCaptor.forClass(byte[].class);
        verify(schemaValidator).validate(validationContent.capture());
        JsonNode validated = objectMapper.readTree(new String(
                validationContent.getValue(),
                StandardCharsets.UTF_8
        ));
        assertEquals("HUMAN", validated.path("generation_type").textValue());
        assertEquals(true, validated.path("source_refs_json").isNull());

        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("CREATE"),
                eq("QUIZ_QUESTION"),
                eq(1201L),
                isNull(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsSchemaFailureBeforeCheckingDatabaseReferences() throws Exception {
        when(schemaValidator.validate(any())).thenReturn(
                QuizQuestionValidationResult.invalid(new QuizQuestionValidationError(
                        QuizQuestionValidationErrorCode.CORRECT_OPTION_NOT_FOUND,
                        "/correct_answer_json/key",
                        "정답 key가 선택지에 없습니다."
                ))
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createQuestion(subChapterRequest(), ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_VALIDATION_FAILED, exception.getErrorCode());
        verify(subChapterMapper, never()).findById(anyLong());
        verify(questionMapper, never()).insert(any());
    }

    @Test
    void rejectsExistingQuestionKey() throws Exception {
        when(questionMapper.countByQuestionKey("deposit-basic-001")).thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createQuestion(subChapterRequest(), ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_KEY_CONFLICT, exception.getErrorCode());
        verify(subChapterMapper, never()).findById(anyLong());
        verify(questionMapper, never()).insert(any());
    }

    @Test
    void rejectsMissingOrMismatchedSubChapterReference() throws Exception {
        when(subChapterMapper.findById(101L)).thenReturn(null);
        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.createQuestion(subChapterRequest(), ACTOR_ID, REQUEST_ID)
        );

        SubChapter subChapter = subChapter(101L, 3L);
        when(subChapterMapper.findById(101L)).thenReturn(subChapter);
        QuizQuestionCreateRequest mismatched = new QuizQuestionCreateRequest(
                "deposit-basic-002",
                "SUB_CHAPTER",
                2L,
                101L,
                "SINGLE_CHOICE",
                "EASY",
                "질문",
                null,
                options(),
                correctAnswer(),
                "해설"
        );
        ApiException mismatch = assertThrows(
                ApiException.class,
                () -> service.createQuestion(mismatched, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_VALIDATION_FAILED, missing.getErrorCode());
        assertEquals(ErrorCode.QUESTION_VALIDATION_FAILED, mismatch.getErrorCode());
        verify(questionMapper, never()).insert(any());
    }

    @Test
    void allowsLevelTestOnlyForAssetMainChapter() throws Exception {
        QuizQuestionCreateRequest levelTest = new QuizQuestionCreateRequest(
                "foundation-level-001",
                "LEVEL_TEST",
                1L,
                null,
                "SINGLE_CHOICE",
                "EASY",
                "질문",
                null,
                options(),
                correctAnswer(),
                "해설"
        );
        when(mainChapterMapper.findById(1L))
                .thenReturn(mainChapter(1L, ChapterType.FOUNDATION));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createQuestion(levelTest, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_VALIDATION_FAILED, exception.getErrorCode());
        verify(questionMapper, never()).insert(any());
    }

    @Test
    void createsNextDraftVersionAndInheritsImmutableMetadata() throws Exception {
        QuizQuestion base = humanQuestion(1201L, 1);
        QuizQuestion latest = humanQuestion(1202L, 2);
        when(questionMapper.findById(1201L)).thenReturn(base);
        when(questionMapper.findLatestByQuestionKeyForUpdate("deposit-basic-001"))
                .thenReturn(latest);

        QuizQuestion created = service.createVersion(
                1201L,
                versionRequest(),
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(3, created.getVersionNo());
        assertEquals(base.getQuestionKey(), created.getQuestionKey());
        assertEquals(base.getUsageType(), created.getUsageType());
        assertEquals(base.getMainChapterId(), created.getMainChapterId());
        assertEquals(base.getSubChapterId(), created.getSubChapterId());
        assertEquals(base.getQuestionType(), created.getQuestionType());
        assertEquals(base.getDifficulty(), created.getDifficulty());
        assertEquals(base.getGenerationType(), created.getGenerationType());
        assertEquals("수정된 질문", created.getPrompt());
        assertEquals(QuizQuestionStatus.DRAFT, created.getStatus());
        verify(questionMapper).findLatestByQuestionKeyForUpdate("deposit-basic-001");
    }

    @Test
    void keepsAiGenerationTypeAndNewSourceReferencesForNewVersion() throws Exception {
        QuizQuestion base = humanQuestion(1301L, 1);
        base.setGenerationType(QuizGenerationType.AI);
        when(questionMapper.findById(1301L)).thenReturn(base);
        when(questionMapper.findLatestByQuestionKeyForUpdate(base.getQuestionKey()))
                .thenReturn(base);
        JsonNode sources = objectMapper.readTree("""
                [{
                  "knowledge_content_id": 9001,
                  "title": "근거",
                  "publisher": null,
                  "url": null,
                  "reference_at": "2026-08-10T00:00:00Z"
                }]
                """);
        QuizQuestionVersionCreateRequest request = new QuizQuestionVersionCreateRequest(
                "수정된 질문",
                null,
                options(),
                correctAnswer(),
                "수정된 해설",
                sources
        );

        QuizQuestion created = service.createVersion(
                1301L,
                request,
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(QuizGenerationType.AI, created.getGenerationType());
        assertEquals(sources, objectMapper.readTree(created.getSourceRefsJson()));
    }

    @Test
    void rejectsVersionForMissingBaseQuestion() throws Exception {
        when(questionMapper.findById(9999L)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createVersion(9999L, versionRequest(), ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_NOT_FOUND, exception.getErrorCode());
        verify(questionMapper, never()).findLatestByQuestionKeyForUpdate(anyString());
    }

    @Test
    void mapsConcurrentVersionInsertToConflict() throws Exception {
        QuizQuestion base = humanQuestion(1201L, 1);
        when(questionMapper.findById(1201L)).thenReturn(base);
        when(questionMapper.findLatestByQuestionKeyForUpdate(base.getQuestionKey()))
                .thenReturn(base);
        doThrow(new DuplicateKeyException("duplicate version"))
                .when(questionMapper).insert(any(QuizQuestion.class));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createVersion(1201L, versionRequest(), ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_VERSION_CONFLICT, exception.getErrorCode());
        verify(auditLogMapper, never()).insert(
                anyLong(), anyString(), anyString(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectsMissingRequestBodyAsInvalidRequest() {
        ApiException create = assertThrows(
                ApiException.class,
                () -> service.createQuestion(null, ACTOR_ID, REQUEST_ID)
        );
        ApiException version = assertThrows(
                ApiException.class,
                () -> service.createVersion(1201L, null, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.INVALID_REQUEST, create.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, version.getErrorCode());
    }

    private QuizQuestionCreateRequest subChapterRequest() throws Exception {
        return new QuizQuestionCreateRequest(
                "deposit-basic-001",
                "SUB_CHAPTER",
                null,
                101L,
                "SINGLE_CHOICE",
                "EASY",
                "예금의 특징으로 적절한 것은?",
                null,
                options(),
                correctAnswer(),
                "정답 해설"
        );
    }

    private QuizQuestionVersionCreateRequest versionRequest() throws Exception {
        return new QuizQuestionVersionCreateRequest(
                "수정된 질문",
                null,
                options(),
                correctAnswer(),
                "수정된 해설",
                null
        );
    }

    private JsonNode options() throws Exception {
        return objectMapper.readTree("""
                [
                  {"key": "1", "label": "선택지 1"},
                  {"key": "2", "label": "선택지 2", "description": null}
                ]
                """);
    }

    private JsonNode correctAnswer() throws Exception {
        return objectMapper.readTree("{\"key\":\"1\"}");
    }

    private MainChapter mainChapter(long id, ChapterType type) {
        MainChapter chapter = MainChapter.create(
                type,
                type == ChapterType.ASSET ? AssetType.DEPOSIT_SAVINGS : null,
                "대단원",
                null,
                1,
                type == ChapterType.FOUNDATION,
                true,
                NOW
        );
        chapter.setMainChapterId(id);
        return chapter;
    }

    private SubChapter subChapter(long id, long mainChapterId) {
        SubChapter chapter = SubChapter.create(
                mainChapterId,
                "소단원",
                null,
                1,
                true,
                NOW
        );
        chapter.setSubChapterId(id);
        return chapter;
    }

    private QuizQuestion humanQuestion(long id, int versionNo) throws Exception {
        QuizQuestion question = QuizQuestion.draft(
                "deposit-basic-001",
                versionNo,
                QuizUsageType.SUB_CHAPTER,
                2L,
                101L,
                null,
                QuizQuestionType.SINGLE_CHOICE,
                QuizDifficulty.EASY,
                "기존 질문",
                null,
                options().toString(),
                correctAnswer().toString(),
                "기존 해설",
                QuizGenerationType.HUMAN,
                null,
                ACTOR_ID,
                NOW.minusDays(1)
        );
        question.setQuestionId(id);
        return question;
    }
}
