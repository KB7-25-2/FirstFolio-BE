package org.firstfolio.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.dto.request.LessonContentUploadRequest;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.validation.LessonContentValidationService;
import org.firstfolio.content.validation.LessonValidationError;
import org.firstfolio.content.validation.LessonValidationErrorCode;
import org.firstfolio.content.validation.LessonValidationResult;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContentVersionService {

    private static final Logger log = LogManager.getLogger(ContentVersionService.class);
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String AUDIT_ENTITY_TYPE = "CONTENT_VERSION";

    private final LessonContentValidationService validationService;
    private final StaticContentStorage contentStorage;
    private final ContentVersionMapper contentVersionMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final Clock clock;
    private final ObjectMapper auditObjectMapper;

    public ContentVersionService(
            LessonContentValidationService validationService,
            StaticContentStorage contentStorage,
            ContentVersionMapper contentVersionMapper,
            AdminAuditLogMapper auditLogMapper,
            Clock clock
    ) {
        this.validationService = validationService;
        this.contentStorage = contentStorage;
        this.contentVersionMapper = contentVersionMapper;
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
        this.auditObjectMapper = ApiObjectMapperFactory.create();
    }

    @Transactional
    public ContentVersion uploadLesson(
            long subChapterId,
            LessonContentUploadRequest request,
            long actorUserId,
            String requestId
    ) {
        requireRequest(request);

        byte[] lessonContent = request.lesson().toString()
                .getBytes(StandardCharsets.UTF_8);
        LessonValidationResult validationResult = validationService.validate(
                subChapterId,
                lessonContent
        );
        requireValidContent(validationResult);

        int versionNo = request.versionNo();
        if (contentVersionMapper.countBySubChapterIdAndVersionNo(
                subChapterId,
                versionNo
        ) > 0) {
            throw new ApiException(ErrorCode.CONTENT_VERSION_CONFLICT);
        }

        String objectKey = lessonObjectKey(subChapterId);
        StoredObjectRef storedObject = contentStorage.store(new ContentWriteRequest(
                objectKey,
                lessonContent,
                CONTENT_TYPE_JSON
        ));

        LocalDateTime now = LocalDateTime.now(clock);
        ContentVersion contentVersion = ContentVersion.draft(
                subChapterId,
                versionNo,
                request.lesson().path("schemaVersion").textValue(),
                storedObject,
                actorUserId,
                now
        );

        try {
            contentVersionMapper.insert(contentVersion);
            auditLogMapper.insert(
                    actorUserId,
                    "CREATE",
                    AUDIT_ENTITY_TYPE,
                    contentVersion.getContentVersionId(),
                    null,
                    snapshot(contentVersion),
                    requestId,
                    now
            );
        } catch (DuplicateKeyException exception) {
            logOrphanedStorageVersion(
                    storedObject,
                    subChapterId,
                    versionNo,
                    requestId,
                    exception
            );
            throw new ApiException(
                    ErrorCode.CONTENT_VERSION_CONFLICT,
                    ErrorCode.CONTENT_VERSION_CONFLICT.getDefaultMessage(),
                    exception
            );
        } catch (RuntimeException exception) {
            logOrphanedStorageVersion(
                    storedObject,
                    subChapterId,
                    versionNo,
                    requestId,
                    exception
            );
            throw exception;
        }
        return contentVersion;
    }

    private void requireRequest(LessonContentUploadRequest request) {
        if (request == null || request.versionNo() == null || request.versionNo() <= 0) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "version_no는 1 이상의 정수여야 합니다."
            );
        }
        if (request.lesson() == null || !request.lesson().isObject()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "lesson은 JSON 객체여야 합니다."
            );
        }
    }

    private void requireValidContent(LessonValidationResult result) {
        if (result.isValid()) {
            return;
        }

        LessonValidationError firstError = result.errors().get(0);
        if (firstError.code() == LessonValidationErrorCode.SUB_CHAPTER_NOT_FOUND) {
            throw new ApiException(ErrorCode.SUB_CHAPTER_NOT_FOUND);
        }

        String location = firstError.path().isBlank()
                ? ""
                : " (" + firstError.path() + ")";
        throw new ApiException(
                ErrorCode.CONTENT_VALIDATION_FAILED,
                ErrorCode.CONTENT_VALIDATION_FAILED.getDefaultMessage()
                        + location + " " + firstError.message()
        );
    }

    private String lessonObjectKey(long subChapterId) {
        return "learning/sub-chapters/" + subChapterId + "/lesson.json";
    }

    private String snapshot(ContentVersion version) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("content_version_id", version.getContentVersionId());
        snapshot.put("sub_chapter_id", version.getSubChapterId());
        snapshot.put("version_no", version.getVersionNo());
        snapshot.put("schema_version", version.getSchemaVersion());
        snapshot.put("storage_object_key", version.getStorageObjectKey());
        snapshot.put("storage_version_id", version.getStorageVersionId());
        snapshot.put("status", version.getStatus());
        snapshot.put("created_by", version.getCreatedBy());
        snapshot.put("created_at", version.getCreatedAt());

        try {
            return auditObjectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("콘텐츠 버전 감사 이력을 직렬화할 수 없습니다.", exception);
        }
    }

    private void logOrphanedStorageVersion(
            StoredObjectRef storedObject,
            long subChapterId,
            int versionNo,
            String requestId,
            RuntimeException exception
    ) {
        log.error(
                "콘텐츠 DB 등록 실패로 저장소 버전 정리 필요 objectKey={} "
                        + "storageVersionId={} subChapterId={} versionNo={} requestId={}",
                storedObject.objectKey(),
                storedObject.versionId(),
                subChapterId,
                versionNo,
                requestId,
                exception
        );
    }
}
