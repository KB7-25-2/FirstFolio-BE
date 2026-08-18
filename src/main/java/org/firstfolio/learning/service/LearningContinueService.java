package org.firstfolio.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningContinueCandidate;
import org.firstfolio.learning.domain.LearningContinueResult;
import org.firstfolio.learning.domain.LearningContinueTarget;
import org.firstfolio.learning.domain.MainChapterQuizContinueCandidate;
import org.firstfolio.learning.mapper.LearningContinueMapper;
import org.firstfolio.learning.mapper.LearningContinueMapper.SubChapterQuizContinueCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class LearningContinueService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final LearningContinueMapper learningContinueMapper;
    private final ContentVersionMapper contentVersionMapper;
    private final StaticContentStorage contentStorage;
    private final ObjectMapper objectMapper;

    public LearningContinueService(
            LearningContinueMapper learningContinueMapper,
            ContentVersionMapper contentVersionMapper,
            StaticContentStorage contentStorage
    ) {
        this.learningContinueMapper = learningContinueMapper;
        this.contentVersionMapper = contentVersionMapper;
        this.contentStorage = contentStorage;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public LearningContinueResult getContinuePosition(long userId) {
        LearningContinueCandidate candidate = learningContinueMapper
                .findLatestInProgress(userId);
        if (candidate == null) {
            return pendingQuizPosition(userId);
        }
        requireCurrentPublishedPosition(candidate);

        ContentVersion version = contentVersionMapper.findById(
                candidate.getContentVersionId()
        );
        if (version == null
                || version.getSubChapterId() != candidate.getSubChapterId()
                || version.getStatus() != ContentVersionStatus.PUBLISHED) {
            throw unavailable(null);
        }

        List<String> pageIds = loadPageIds(version);
        String lastPageId = candidate.getLastPageId();
        int progressPercent = calculateProgressPercent(pageIds, lastPageId);
        return new LearningContinueResult(
                LearningContinueTarget.LESSON,
                candidate.getCurriculumItemId(),
                candidate.getMainChapterId(),
                candidate.getSubChapterId(),
                candidate.getContentVersionId(),
                null,
                lastPageId,
                progressPercent,
                route(candidate.getSubChapterId(), lastPageId)
        );
    }

    private LearningContinueResult pendingQuizPosition(long userId) {
        SubChapterQuizContinueCandidate subChapterQuiz = learningContinueMapper
                .findSubChapterQuizCandidate(userId);
        if (subChapterQuiz != null) {
            return new LearningContinueResult(
                    LearningContinueTarget.SUB_CHAPTER_QUIZ,
                    subChapterQuiz.curriculumItemId(),
                    subChapterQuiz.mainChapterId(),
                    subChapterQuiz.subChapterId(),
                    null,
                    subChapterQuiz.attemptId(),
                    null,
                    100,
                    subChapterQuizRoute(subChapterQuiz.subChapterId())
            );
        }
        return mainChapterQuizPosition(userId);
    }

    private LearningContinueResult mainChapterQuizPosition(long userId) {
        MainChapterQuizContinueCandidate candidate = learningContinueMapper
                .findMainChapterQuizCandidate(userId);
        if (candidate == null) {
            throw new ApiException(ErrorCode.CONTINUE_POSITION_NOT_FOUND);
        }
        return new LearningContinueResult(
                LearningContinueTarget.MAIN_CHAPTER_QUIZ,
                candidate.getCurriculumItemId(),
                candidate.getMainChapterId(),
                null,
                null,
                candidate.getAttemptId(),
                null,
                100,
                mainChapterQuizRoute(candidate.getMainChapterId())
        );
    }

    private void requireCurrentPublishedPosition(
            LearningContinueCandidate candidate
    ) {
        if (!candidate.isMainChapterActive()
                || !candidate.isSubChapterActive()
                || !Objects.equals(
                        candidate.getCurrentContentVersionId(),
                        candidate.getContentVersionId()
                )) {
            throw unavailable(null);
        }
    }

    private List<String> loadPageIds(ContentVersion version) {
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

        try {
            JsonNode lesson = objectMapper.readTree(new String(
                    stored.content(),
                    StandardCharsets.UTF_8
            ));
            if (lesson == null
                    || !lesson.isObject()
                    || !Objects.equals(
                            version.getSchemaVersion(),
                            lesson.path("schemaVersion").textValue()
                    )) {
                throw unavailable(null);
            }

            JsonNode pages = lesson.path("pages");
            if (!pages.isArray() || pages.isEmpty()) {
                throw unavailable(null);
            }
            List<String> pageIds = new ArrayList<>();
            Set<String> uniquePageIds = new HashSet<>();
            for (JsonNode page : pages) {
                String pageId = page.path("id").textValue();
                if (pageId == null
                        || pageId.isBlank()
                        || !uniquePageIds.add(pageId)) {
                    throw unavailable(null);
                }
                pageIds.add(pageId);
            }
            return List.copyOf(pageIds);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private int calculateProgressPercent(
            List<String> pageIds,
            String lastPageId
    ) {
        if (lastPageId == null) {
            return 0;
        }
        int pageIndex = pageIds.indexOf(lastPageId);
        if (pageIndex < 0) {
            throw unavailable(null);
        }
        return Math.round((pageIndex + 1) * 100.0f / pageIds.size());
    }

    private String route(long subChapterId, String lastPageId) {
        String baseRoute = "/learning/sub-chapters/" + subChapterId;
        return lastPageId == null
                ? baseRoute
                : baseRoute + "?page=" + lastPageId;
    }

    private String mainChapterQuizRoute(long mainChapterId) {
        return "/learning/main-chapters/" + mainChapterId
                + "/scenario-quiz";
    }

    private String subChapterQuizRoute(long subChapterId) {
        return "/learning/sub-chapters/" + subChapterId + "/quiz";
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
}
