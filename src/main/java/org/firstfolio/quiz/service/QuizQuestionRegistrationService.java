package org.firstfolio.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.firstfolio.common.json.ApiObjectMapperFactory;
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
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.dto.request.QuizQuestionCreateRequest;
import org.firstfolio.quiz.dto.request.QuizQuestionVersionCreateRequest;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.firstfolio.quiz.validation.QuizQuestionSchemaValidator;
import org.firstfolio.quiz.validation.QuizQuestionValidationError;
import org.firstfolio.quiz.validation.QuizQuestionValidationResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class QuizQuestionRegistrationService {

    private static final String AUDIT_ENTITY_TYPE = "QUIZ_QUESTION";

    private final QuizQuestionSchemaValidator schemaValidator;
    private final QuizQuestionMapper questionMapper;
    private final MainChapterMapper mainChapterMapper;
    private final SubChapterMapper subChapterMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public QuizQuestionRegistrationService(
            QuizQuestionSchemaValidator schemaValidator,
            QuizQuestionMapper questionMapper,
            MainChapterMapper mainChapterMapper,
            SubChapterMapper subChapterMapper,
            AdminAuditLogMapper auditLogMapper,
            Clock clock
    ) {
        this.schemaValidator = schemaValidator;
        this.questionMapper = questionMapper;
        this.mainChapterMapper = mainChapterMapper;
        this.subChapterMapper = subChapterMapper;
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
        this.objectMapper = ApiObjectMapperFactory.create();
    }

    @Transactional
    public QuizQuestion createQuestion(
            QuizQuestionCreateRequest request,
            long actorUserId,
            String requestId
    ) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        ObjectNode candidate = firstVersionCandidate(request);
        requireValidQuestion(candidate);

        if (questionMapper.countByQuestionKey(request.questionKey()) > 0) {
            throw new ApiException(ErrorCode.QUESTION_KEY_CONFLICT);
        }

        QuizUsageType usageType = QuizUsageType.valueOf(request.usageType());
        QuizQuestionType questionType = QuizQuestionType.valueOf(request.questionType());
        QuizDifficulty difficulty = request.difficulty() == null
                ? null
                : QuizDifficulty.valueOf(request.difficulty());
        QuestionScope scope = resolveAndValidateScope(
                usageType,
                request.mainChapterId(),
                request.subChapterId()
        );
        LocalDateTime now = LocalDateTime.now(clock);
        QuizQuestion question = QuizQuestion.draft(
                request.questionKey(),
                1,
                usageType,
                scope.mainChapterId(),
                scope.subChapterId(),
                null,
                questionType,
                difficulty,
                request.prompt(),
                jsonOrNull(request.scenarioJson()),
                request.optionsJson().toString(),
                request.correctAnswerJson().toString(),
                request.explanation(),
                QuizGenerationType.HUMAN,
                null,
                actorUserId,
                now
        );

        insertQuestion(
                question,
                actorUserId,
                requestId,
                now,
                ErrorCode.QUESTION_KEY_CONFLICT
        );
        return question;
    }

    @Transactional
    public QuizQuestion createVersion(
            long questionId,
            QuizQuestionVersionCreateRequest request,
            long actorUserId,
            String requestId
    ) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        QuizQuestion base = questionMapper.findById(questionId);
        if (base == null) {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND);
        }

        QuizQuestion latest = questionMapper.findLatestByQuestionKeyForUpdate(
                base.getQuestionKey()
        );
        if (latest == null) {
            throw new IllegalStateException("퀴즈 문항의 최신 버전을 찾을 수 없습니다.");
        }

        ObjectNode candidate = nextVersionCandidate(base, request);
        requireValidQuestion(candidate);

        int versionNo;
        try {
            versionNo = Math.addExact(latest.getVersionNo(), 1);
        } catch (ArithmeticException exception) {
            throw new ApiException(
                    ErrorCode.QUESTION_VERSION_CONFLICT,
                    ErrorCode.QUESTION_VERSION_CONFLICT.getDefaultMessage(),
                    exception
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        QuizQuestion question = QuizQuestion.draft(
                base.getQuestionKey(),
                versionNo,
                base.getUsageType(),
                base.getMainChapterId(),
                base.getSubChapterId(),
                base.getDisplayOrder(),
                base.getQuestionType(),
                base.getDifficulty(),
                request.prompt(),
                jsonOrNull(request.scenarioJson()),
                request.optionsJson().toString(),
                request.correctAnswerJson().toString(),
                request.explanation(),
                base.getGenerationType(),
                jsonOrNull(request.sourceRefsJson()),
                actorUserId,
                now
        );

        insertQuestion(
                question,
                actorUserId,
                requestId,
                now,
                ErrorCode.QUESTION_VERSION_CONFLICT
        );
        return question;
    }

    private ObjectNode firstVersionCandidate(QuizQuestionCreateRequest request) {
        ObjectNode candidate = objectMapper.createObjectNode();
        putText(candidate, "question_key", request.questionKey());
        putText(candidate, "usage_type", request.usageType());
        putLong(candidate, "main_chapter_id", request.mainChapterId());
        putLong(candidate, "sub_chapter_id", request.subChapterId());
        putText(candidate, "question_type", request.questionType());
        putText(candidate, "difficulty", request.difficulty());
        putText(candidate, "prompt", request.prompt());
        setNode(candidate, "scenario_json", request.scenarioJson());
        setNode(candidate, "options_json", request.optionsJson());
        setNode(candidate, "correct_answer_json", request.correctAnswerJson());
        putText(candidate, "explanation", request.explanation());
        candidate.put("generation_type", QuizGenerationType.HUMAN.name());
        candidate.putNull("source_refs_json");
        return candidate;
    }

    private ObjectNode nextVersionCandidate(
            QuizQuestion base,
            QuizQuestionVersionCreateRequest request
    ) {
        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.put("question_key", base.getQuestionKey());
        candidate.put("usage_type", base.getUsageType().name());
        putLong(candidate, "main_chapter_id", base.getMainChapterId());
        putLong(candidate, "sub_chapter_id", base.getSubChapterId());
        candidate.put("question_type", base.getQuestionType().name());
        putEnum(candidate, "difficulty", base.getDifficulty());
        putText(candidate, "prompt", request.prompt());
        setNode(candidate, "scenario_json", request.scenarioJson());
        setNode(candidate, "options_json", request.optionsJson());
        setNode(candidate, "correct_answer_json", request.correctAnswerJson());
        putText(candidate, "explanation", request.explanation());
        candidate.put("generation_type", base.getGenerationType().name());
        setNode(candidate, "source_refs_json", request.sourceRefsJson());
        return candidate;
    }

    private QuestionScope resolveAndValidateScope(
            QuizUsageType usageType,
            Long mainChapterId,
            Long subChapterId
    ) {
        Long resolvedMainChapterId = mainChapterId;

        if (subChapterId != null) {
            SubChapter subChapter = subChapterMapper.findById(subChapterId);
            if (subChapter == null) {
                throw validationFailure(
                        "/sub_chapter_id",
                        "참조한 소단원을 찾을 수 없습니다: " + subChapterId
                );
            }
            if (mainChapterId != null
                    && mainChapterId.longValue() != subChapter.getMainChapterId()) {
                throw validationFailure(
                        "/main_chapter_id",
                        "대단원과 소단원의 소속 관계가 일치하지 않습니다."
                );
            }
            if (resolvedMainChapterId == null) {
                resolvedMainChapterId = subChapter.getMainChapterId();
            }
        }

        MainChapter mainChapter = null;
        if (resolvedMainChapterId != null) {
            mainChapter = mainChapterMapper.findById(resolvedMainChapterId);
            if (mainChapter == null) {
                throw validationFailure(
                        "/main_chapter_id",
                        "참조한 대단원을 찾을 수 없습니다: " + resolvedMainChapterId
                );
            }
        }

        if (usageType == QuizUsageType.LEVEL_TEST
                && (mainChapter == null
                || mainChapter.getChapterType() != ChapterType.ASSET)) {
            throw validationFailure(
                    "/main_chapter_id",
                    "레벨 테스트는 ASSET 대단원만 참조할 수 있습니다."
            );
        }
        return new QuestionScope(resolvedMainChapterId, subChapterId);
    }

    private void requireValidQuestion(ObjectNode candidate) {
        QuizQuestionValidationResult result = schemaValidator.validate(
                candidate.toString().getBytes(StandardCharsets.UTF_8)
        );
        if (result.isValid()) {
            return;
        }

        QuizQuestionValidationError firstError = result.errors().get(0);
        throw validationFailure(firstError.path(), firstError.message());
    }

    private ApiException validationFailure(String path, String message) {
        String location = path == null || path.isBlank() ? "" : " (" + path + ")";
        return new ApiException(
                ErrorCode.QUESTION_VALIDATION_FAILED,
                ErrorCode.QUESTION_VALIDATION_FAILED.getDefaultMessage()
                        + location + " " + message
        );
    }

    private void insertQuestion(
            QuizQuestion question,
            long actorUserId,
            String requestId,
            LocalDateTime now,
            ErrorCode conflictCode
    ) {
        try {
            questionMapper.insert(question);
            auditLogMapper.insert(
                    actorUserId,
                    "CREATE",
                    AUDIT_ENTITY_TYPE,
                    question.getQuestionId(),
                    null,
                    snapshot(question),
                    requestId,
                    now
            );
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    conflictCode,
                    conflictCode.getDefaultMessage(),
                    exception
            );
        }
    }

    private String snapshot(QuizQuestion question) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("question_id", question.getQuestionId());
        snapshot.put("question_key", question.getQuestionKey());
        snapshot.put("version_no", question.getVersionNo());
        snapshot.put("usage_type", question.getUsageType());
        snapshot.put("main_chapter_id", question.getMainChapterId());
        snapshot.put("sub_chapter_id", question.getSubChapterId());
        snapshot.put("question_type", question.getQuestionType());
        snapshot.put("difficulty", question.getDifficulty());
        snapshot.put("prompt", question.getPrompt());
        snapshot.put("scenario_json", parseStoredJson(question.getScenarioJson()));
        snapshot.put("options_json", parseStoredJson(question.getOptionsJson()));
        snapshot.put("correct_answer_json", parseStoredJson(question.getCorrectAnswerJson()));
        snapshot.put("explanation", question.getExplanation());
        snapshot.put("generation_type", question.getGenerationType());
        snapshot.put("source_refs_json", parseStoredJson(question.getSourceRefsJson()));
        snapshot.put("status", question.getStatus());
        snapshot.put("created_by", question.getCreatedBy());
        snapshot.put("created_at", question.getCreatedAt());

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("퀴즈 문항 감사 이력을 직렬화할 수 없습니다.", exception);
        }
    }

    private JsonNode parseStoredJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("퀴즈 문항 JSON을 감사 이력으로 변환할 수 없습니다.", exception);
        }
    }

    private String jsonOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.toString();
    }

    private void putText(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private void putLong(ObjectNode target, String field, Long value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private void putEnum(ObjectNode target, String field, Enum<?> value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value.name());
        }
    }

    private void setNode(ObjectNode target, String field, JsonNode value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.set(field, value);
        }
    }

    private record QuestionScope(Long mainChapterId, Long subChapterId) {
    }
}
