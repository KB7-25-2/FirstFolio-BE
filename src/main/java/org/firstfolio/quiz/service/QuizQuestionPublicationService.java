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
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.firstfolio.quiz.validation.QuizQuestionSchemaValidator;
import org.firstfolio.quiz.validation.QuizQuestionValidationError;
import org.firstfolio.quiz.validation.QuizQuestionValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class QuizQuestionPublicationService {

    private static final String AUDIT_ENTITY_TYPE = "QUIZ_QUESTION";

    private final QuizQuestionSchemaValidator schemaValidator;
    private final QuizQuestionMapper questionMapper;
    private final MainChapterMapper mainChapterMapper;
    private final SubChapterMapper subChapterMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public QuizQuestionPublicationService(
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
    public QuizQuestion publish(
            long questionId,
            long actorUserId,
            String requestId
    ) {
        QuizQuestion reference = questionMapper.findById(questionId);
        if (reference == null) {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND);
        }

        QuizQuestion target = questionMapper.findLatestByQuestionKeyForUpdate(
                reference.getQuestionKey()
        );
        if (target == null) {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND);
        }
        if (!Objects.equals(target.getQuestionId(), questionId)
                || (target.getStatus() != QuizQuestionStatus.DRAFT
                && target.getStatus() != QuizQuestionStatus.REVIEW)) {
            throw new ApiException(ErrorCode.QUESTION_NOT_PUBLISHABLE);
        }

        requireValidForPublication(target);

        LocalDateTime now = LocalDateTime.now(clock);
        retirePublishedVersions(
                target.getQuestionKey(),
                actorUserId,
                requestId,
                now
        );

        String before = snapshot(target);
        if (questionMapper.publishDraft(questionId, now) != 1) {
            throw new ApiException(ErrorCode.QUESTION_NOT_PUBLISHABLE);
        }
        target.publish(now);
        insertAuditLog(
                actorUserId,
                "PUBLISH",
                target,
                before,
                requestId,
                now
        );
        return target;
    }

    @Transactional
    public QuizQuestion submitForReview(
            long questionId,
            long actorUserId,
            String requestId
    ) {
        QuizQuestion target = questionMapper.findByIdForUpdate(questionId);
        if (target == null) {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND);
        }
        if (target.getStatus() != QuizQuestionStatus.DRAFT) {
            throw new ApiException(ErrorCode.QUESTION_NOT_REVIEWABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String before = snapshot(target);
        if (questionMapper.submitForReview(questionId) != 1) {
            throw new ApiException(ErrorCode.QUESTION_NOT_REVIEWABLE);
        }
        target.setStatus(QuizQuestionStatus.REVIEW);
        insertAuditLog(
                actorUserId,
                "SUBMIT_REVIEW",
                target,
                before,
                requestId,
                now
        );
        return target;
    }

    @Transactional
    public QuizQuestion retire(
            long questionId,
            long actorUserId,
            String requestId
    ) {
        QuizQuestion target = questionMapper.findByIdForUpdate(questionId);
        if (target == null) {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND);
        }
        if (target.getStatus() != QuizQuestionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.QUESTION_NOT_RETIRABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String before = snapshot(target);
        if (questionMapper.retirePublished(questionId) != 1) {
            throw new ApiException(ErrorCode.QUESTION_NOT_RETIRABLE);
        }
        target.retire();
        insertAuditLog(
                actorUserId,
                "RETIRE",
                target,
                before,
                requestId,
                now
        );
        return target;
    }

    private void retirePublishedVersions(
            String questionKey,
            long actorUserId,
            String requestId,
            LocalDateTime now
    ) {
        List<QuizQuestion> publishedVersions =
                questionMapper.findPublishedByQuestionKeyForUpdate(questionKey);
        for (QuizQuestion published : publishedVersions) {
            String before = snapshot(published);
            if (questionMapper.retirePublished(published.getQuestionId()) != 1) {
                throw new IllegalStateException("기존 공개 퀴즈 문항을 폐기하지 못했습니다.");
            }
            published.retire();
            insertAuditLog(
                    actorUserId,
                    "RETIRE",
                    published,
                    before,
                    requestId,
                    now
            );
        }
    }

    private void requireValidForPublication(QuizQuestion question) {
        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.put("question_key", question.getQuestionKey());
        candidate.put("usage_type", question.getUsageType().name());
        putLong(candidate, "main_chapter_id", question.getMainChapterId());
        putLong(candidate, "sub_chapter_id", question.getSubChapterId());
        putInteger(candidate, "display_order", question.getDisplayOrder());
        candidate.put("question_type", question.getQuestionType().name());
        if (question.getDifficulty() == null) {
            candidate.putNull("difficulty");
        } else {
            candidate.put("difficulty", question.getDifficulty().name());
        }
        candidate.put("prompt", question.getPrompt());
        setStoredJson(candidate, "scenario_json", question.getScenarioJson());
        setStoredJson(candidate, "options_json", question.getOptionsJson());
        setStoredJson(candidate, "correct_answer_json", question.getCorrectAnswerJson());
        candidate.put("explanation", question.getExplanation());
        candidate.put("generation_type", question.getGenerationType().name());
        if (question.getQuestDate() == null) {
            candidate.putNull("quest_date");
        } else {
            candidate.put("quest_date", question.getQuestDate().toString());
        }
        setStoredJson(candidate, "source_refs_json", question.getSourceRefsJson());

        QuizQuestionValidationResult result = schemaValidator.validate(
                candidate.toString().getBytes(StandardCharsets.UTF_8)
        );
        if (!result.isValid()) {
            QuizQuestionValidationError error = result.errors().get(0);
            throw validationFailure(error.path(), error.message());
        }

        validateChapterReferences(question);
    }

    private void validateChapterReferences(QuizQuestion question) {
        SubChapter subChapter = null;
        if (question.getSubChapterId() != null) {
            subChapter = subChapterMapper.findById(question.getSubChapterId());
            if (subChapter == null) {
                throw validationFailure(
                        "/sub_chapter_id",
                        "참조한 소단원을 찾을 수 없습니다: " + question.getSubChapterId()
                );
            }
            if (!Objects.equals(
                    question.getMainChapterId(),
                    subChapter.getMainChapterId()
            )) {
                throw validationFailure(
                        "/main_chapter_id",
                        "대단원과 소단원의 소속 관계가 일치하지 않습니다."
                );
            }
        }

        MainChapter mainChapter = null;
        if (question.getMainChapterId() != null) {
            mainChapter = mainChapterMapper.findById(question.getMainChapterId());
            if (mainChapter == null) {
                throw validationFailure(
                        "/main_chapter_id",
                        "참조한 대단원을 찾을 수 없습니다: " + question.getMainChapterId()
                );
            }
        }

        if (question.getUsageType() == QuizUsageType.LEVEL_TEST
                && (mainChapter == null
                || mainChapter.getChapterType() != ChapterType.ASSET)) {
            throw validationFailure(
                    "/main_chapter_id",
                    "레벨 테스트는 ASSET 대단원만 참조할 수 있습니다."
            );
        }
    }

    private void insertAuditLog(
            long actorUserId,
            String action,
            QuizQuestion question,
            String before,
            String requestId,
            LocalDateTime now
    ) {
        if (auditLogMapper.insert(
                actorUserId,
                action,
                AUDIT_ENTITY_TYPE,
                question.getQuestionId(),
                before,
                snapshot(question),
                requestId,
                now
        ) != 1) {
            throw new IllegalStateException("퀴즈 문항 상태 변경 감사 이력을 저장하지 못했습니다.");
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
        snapshot.put("display_order", question.getDisplayOrder());
        snapshot.put("question_type", question.getQuestionType());
        snapshot.put("difficulty", question.getDifficulty());
        snapshot.put("prompt", question.getPrompt());
        snapshot.put("scenario_json", parseStoredJson(question.getScenarioJson()));
        snapshot.put("options_json", parseStoredJson(question.getOptionsJson()));
        snapshot.put("correct_answer_json", parseStoredJson(question.getCorrectAnswerJson()));
        snapshot.put("explanation", question.getExplanation());
        snapshot.put("generation_type", question.getGenerationType());
        snapshot.put("quest_date", question.getQuestDate());
        snapshot.put("source_refs_json", parseStoredJson(question.getSourceRefsJson()));
        snapshot.put("status", question.getStatus());
        snapshot.put("published_at", question.getPublishedAt());
        snapshot.put("created_by", question.getCreatedBy());
        snapshot.put("created_at", question.getCreatedAt());

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("퀴즈 문항 감사 이력을 직렬화할 수 없습니다.", exception);
        }
    }

    private void setStoredJson(ObjectNode target, String field, String json) {
        if (json == null) {
            target.putNull(field);
            return;
        }
        target.set(field, parseStoredJson(json));
    }

    private JsonNode parseStoredJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw validationFailure("", "저장된 문항 JSON이 올바르지 않습니다.");
        }
    }

    private void putLong(ObjectNode target, String field, Long value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private void putInteger(ObjectNode target, String field, Integer value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private ApiException validationFailure(String path, String message) {
        String location = path == null || path.isBlank() ? "" : " (" + path + ")";
        return new ApiException(
                ErrorCode.QUESTION_VALIDATION_FAILED,
                ErrorCode.QUESTION_VALIDATION_FAILED.getDefaultMessage()
                        + location + " " + message
        );
    }
}
