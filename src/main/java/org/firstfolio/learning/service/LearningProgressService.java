package org.firstfolio.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.PublishedLessonReference;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressEvent;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.domain.LearningProgressStatusResult;
import org.firstfolio.learning.domain.LearningProgressUpdateCommand;
import org.firstfolio.learning.domain.LearningProgressUpdateResult;
import org.firstfolio.learning.domain.SubChapterQuizProgress;
import org.firstfolio.learning.mapper.LearningProgressMapper;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class LearningProgressService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final LearningProgressMapper learningProgressMapper;
    private final ContentVersionMapper contentVersionMapper;
    private final SubChapterMapper subChapterMapper;
    private final QuizAttemptMapper quizAttemptMapper;
    private final StaticContentStorage contentStorage;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public LearningProgressService(
            LearningProgressMapper learningProgressMapper,
            ContentVersionMapper contentVersionMapper,
            SubChapterMapper subChapterMapper,
            QuizAttemptMapper quizAttemptMapper,
            StaticContentStorage contentStorage,
            Clock clock
    ) {
        this.learningProgressMapper = learningProgressMapper;
        this.contentVersionMapper = contentVersionMapper;
        this.subChapterMapper = subChapterMapper;
        this.quizAttemptMapper = quizAttemptMapper;
        this.contentStorage = contentStorage;
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public LearningProgressUpdateResult save(
            long userId,
            long subChapterId,
            LearningProgressUpdateCommand command
    ) {
        requireActiveSubChapter(subChapterId);
        LearningProgress progress = learningProgressMapper
                .findByUserIdAndSubChapterIdForUpdate(userId, subChapterId);

        if (progress == null) {
            progress = createFirstProgress(userId, subChapterId, command);
            if (progress != null) {
                return new LearningProgressUpdateResult(progress, true);
            }

            progress = learningProgressMapper
                    .findByUserIdAndSubChapterIdForUpdate(userId, subChapterId);
            if (progress == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }

        return updateExistingProgress(progress, command);
    }

    @Transactional(readOnly = true)
    public LearningProgressStatusResult getStatus(
            long userId,
            long subChapterId
    ) {
        requireActiveSubChapter(subChapterId);
        LearningProgress progress = learningProgressMapper
                .findByUserIdAndSubChapterId(userId, subChapterId);

        if (progress != null) {
            return statusResult(userId, subChapterId, progress);
        }

        PublishedLessonReference published = contentVersionMapper
                .findCurrentPublishedLesson(subChapterId);
        if (published == null) {
            throw new ApiException(ErrorCode.CONTENT_NOT_PUBLISHED);
        }

        LearningProgress notStarted = new LearningProgress();
        notStarted.setUserId(userId);
        notStarted.setSubChapterId(subChapterId);
        notStarted.setContentVersionId(published.getContentVersionId());
        notStarted.setStatus(LearningProgressStatus.NOT_STARTED);
        return statusResult(userId, subChapterId, notStarted);
    }

    private LearningProgressStatusResult statusResult(
            long userId,
            long subChapterId,
            LearningProgress progress
    ) {
        SubChapterQuizProgress quizProgress = quizAttemptMapper
                .findSubChapterQuizProgress(userId, subChapterId);
        if (quizProgress == null
                || quizProgress.getAnsweredCount() < 0
                || quizProgress.getTotalCount() < 0
                || quizProgress.getAnsweredCount() > quizProgress.getTotalCount()
                || (quizProgress.getActiveAttemptId() == null
                && (quizProgress.getAnsweredCount() != 0
                || quizProgress.getTotalCount() != 0))) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return new LearningProgressStatusResult(progress, quizProgress);
    }

    private LearningProgress createFirstProgress(
            long userId,
            long subChapterId,
            LearningProgressUpdateCommand command
    ) {
        PublishedLessonReference published = contentVersionMapper
                .findCurrentPublishedLesson(subChapterId);
        if (published == null) {
            throw new ApiException(ErrorCode.CONTENT_NOT_PUBLISHED);
        }
        if (published.getContentVersionId() != command.contentVersionId()) {
            throw new ApiException(ErrorCode.CONTENT_VERSION_MISMATCH);
        }

        validatePageId(subChapterId, command.contentVersionId(), command.lastPageId());

        LocalDateTime now = LocalDateTime.now(clock);
        LearningProgress created = new LearningProgress();
        created.setUserId(userId);
        created.setSubChapterId(subChapterId);
        created.setContentVersionId(command.contentVersionId());
        created.setLastPageId(command.lastPageId());
        created.setStatus(command.status());
        created.setStartedAt(now);
        created.setCompletedAt(command.status() == LearningProgressStatus.COMPLETED
                ? now : null);
        created.setUpdatedAt(now);

        if (learningProgressMapper.insertIfAbsent(created) != 1) {
            return null;
        }

        LearningProgress stored = learningProgressMapper
                .findByUserIdAndSubChapterIdForUpdate(userId, subChapterId);
        if (stored == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        insertEvent(
                stored,
                LearningProgressStatus.NOT_STARTED,
                null,
                eventType(command.status()),
                now
        );
        return stored;
    }

    private LearningProgressUpdateResult updateExistingProgress(
            LearningProgress progress,
            LearningProgressUpdateCommand command
    ) {
        if (progress.getContentVersionId() != command.contentVersionId()) {
            throw new ApiException(ErrorCode.CONTENT_VERSION_MISMATCH);
        }

        String targetPageId = command.lastPageId() == null
                ? progress.getLastPageId() : command.lastPageId();
        List<String> pageIds = validatePageId(
                progress.getSubChapterId(),
                command.contentVersionId(),
                targetPageId
        );

        if (progress.getStatus() == LearningProgressStatus.COMPLETED) {
            return new LearningProgressUpdateResult(progress, false);
        }

        if (isBackwardMove(pageIds, progress.getLastPageId(), targetPageId)) {
            return new LearningProgressUpdateResult(progress, false);
        }

        boolean changed = progress.getStatus() != command.status()
                || !Objects.equals(progress.getLastPageId(), targetPageId);
        if (!changed) {
            return new LearningProgressUpdateResult(progress, false);
        }

        LearningProgressStatus previousStatus = progress.getStatus();
        String previousPageId = progress.getLastPageId();
        LocalDateTime now = LocalDateTime.now(clock);

        progress.setLastPageId(targetPageId);
        progress.setStatus(command.status());
        progress.setUpdatedAt(now);
        if (command.status() == LearningProgressStatus.COMPLETED) {
            progress.setCompletedAt(now);
        }

        if (learningProgressMapper.updateProgress(progress) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }

        insertEvent(
                progress,
                previousStatus,
                previousPageId,
                eventType(command.status()),
                now
        );
        return new LearningProgressUpdateResult(progress, true);
    }

    private List<String> validatePageId(
            long subChapterId,
            long contentVersionId,
            String lastPageId
    ) {
        if (lastPageId == null) {
            return List.of();
        }

        ContentVersion version = contentVersionMapper.findById(contentVersionId);
        if (version == null || version.getSubChapterId() != subChapterId) {
            throw new ApiException(ErrorCode.CONTENT_VERSION_MISMATCH);
        }

        StoredContent stored;
        try {
            stored = contentStorage.load(new StoredObjectRef(
                    version.getStorageObjectKey(),
                    version.getStorageVersionId()
            ));
        } catch (ContentStorageException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }

        if (!isJson(stored.contentType())) {
            throw unavailable(null);
        }

        JsonNode lesson;
        try {
            lesson = objectMapper.readTree(new String(
                    stored.content(),
                    StandardCharsets.UTF_8
            ));
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }

        if (lesson == null
                || !lesson.isObject()
                || !version.getSchemaVersion().equals(
                        lesson.path("schemaVersion").textValue()
                )) {
            throw unavailable(null);
        }

        List<String> pageIds = new ArrayList<>();
        for (JsonNode page : lesson.path("pages")) {
            pageIds.add(page.path("id").textValue());
        }
        if (!pageIds.contains(lastPageId)) {
            throw new ApiException(ErrorCode.INVALID_PAGE_ID);
        }
        return pageIds;
    }

    private boolean isBackwardMove(
            List<String> pageIds,
            String previousPageId,
            String requestedPageId
    ) {
        if (previousPageId == null || requestedPageId == null) {
            return false;
        }

        int previousIndex = pageIds.indexOf(previousPageId);
        if (previousIndex < 0) {
            throw unavailable(null);
        }
        return pageIds.indexOf(requestedPageId) < previousIndex;
    }

    private void insertEvent(
            LearningProgress progress,
            LearningProgressStatus previousStatus,
            String previousPageId,
            String eventType,
            LocalDateTime occurredAt
    ) {
        LearningProgressEvent event = new LearningProgressEvent();
        event.setProgressId(progress.getProgressId());
        event.setUserId(progress.getUserId());
        event.setSubChapterId(progress.getSubChapterId());
        event.setContentVersionId(progress.getContentVersionId());
        event.setEventType(eventType);
        event.setPreviousStatus(previousStatus);
        event.setStatus(progress.getStatus());
        event.setPreviousPageId(previousPageId);
        event.setLastPageId(progress.getLastPageId());
        event.setOccurredAt(occurredAt);
        if (learningProgressMapper.insertEvent(event) != 1) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String eventType(LearningProgressStatus status) {
        return status == LearningProgressStatus.COMPLETED
                ? "COMPLETED" : "PROGRESS_UPDATED";
    }

    private boolean isJson(String contentType) {
        return JSON_CONTENT_TYPE.equalsIgnoreCase(
                contentType.split(";", 2)[0].trim()
        );
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(
                ErrorCode.CONTENT_UNAVAILABLE,
                ErrorCode.CONTENT_UNAVAILABLE.getDefaultMessage(),
                cause
        );
    }

    private void requireActiveSubChapter(long subChapterId) {
        SubChapter subChapter = subChapterMapper.findById(subChapterId);
        if (subChapter == null || !subChapter.isActive()) {
            throw new ApiException(ErrorCode.SUB_CHAPTER_NOT_FOUND);
        }
    }
}
