package org.firstfolio.curriculum.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.dto.request.MainChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.MainChapterPatchRequest;
import org.firstfolio.curriculum.dto.request.SubChapterCreateRequest;
import org.firstfolio.curriculum.dto.request.SubChapterPatchRequest;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChapterMetadataService {

    private static final int TITLE_MAX_LENGTH = 100;
    private static final String MAIN_CHAPTER_ENTITY = "MAIN_CHAPTER";
    private static final String SUB_CHAPTER_ENTITY = "SUB_CHAPTER";

    private final MainChapterMapper mainChapterMapper;
    private final SubChapterMapper subChapterMapper;
    private final AdminAuditLogMapper adminAuditLogMapper;
    private final Clock clock;
    private final ObjectMapper auditObjectMapper;

    public ChapterMetadataService(
            MainChapterMapper mainChapterMapper,
            SubChapterMapper subChapterMapper,
            AdminAuditLogMapper adminAuditLogMapper,
            Clock clock
    ) {
        this.mainChapterMapper = mainChapterMapper;
        this.subChapterMapper = subChapterMapper;
        this.adminAuditLogMapper = adminAuditLogMapper;
        this.clock = clock;
        this.auditObjectMapper = ApiObjectMapperFactory.create();
    }

    @Transactional(readOnly = true)
    public List<MainChapter> getAllMainChapters(
            ChapterType chapterType,
            Boolean active
    ) {
        return mainChapterMapper.findAll(chapterType, active);
    }

    @Transactional(readOnly = true)
    public List<SubChapter> getAllSubChapters(long mainChapterId) {
        requireMainChapter(mainChapterId);
        return subChapterMapper.findAllByMainChapterId(mainChapterId);
    }

    @Transactional
    public MainChapter createMainChapter(
            MainChapterCreateRequest request,
            long actorUserId,
            String requestId
    ) {
        requireMainCreateRequest(request);
        ChapterType chapterType = request.chapterType();
        AssetType assetType = request.assetType();
        boolean required = request.isRequired();

        validateMainChapterType(chapterType, assetType, required);
        if (chapterType == ChapterType.FOUNDATION
                && mainChapterMapper.countActiveByChapterType(
                        ChapterType.FOUNDATION
                ) > 0) {
            throw new ApiException(ErrorCode.FOUNDATION_CONFLICT);
        }

        String title = normalizeMainTitle(request.title());
        String description = normalizeDescription(request.description());
        int displayOrder = requireMainDisplayOrder(request.displayOrder());
        validateRequiredFoundationOrder(required, displayOrder);

        LocalDateTime now = LocalDateTime.now(clock);
        MainChapter chapter = MainChapter.create(
                chapterType,
                assetType,
                title,
                description,
                displayOrder,
                required,
                true,
                now
        );
        mainChapterMapper.insert(chapter);
        insertAudit(
                actorUserId,
                "CREATE",
                MAIN_CHAPTER_ENTITY,
                chapter.getMainChapterId(),
                null,
                mainChapterSnapshot(chapter),
                requestId,
                now
        );
        return chapter;
    }

    @Transactional
    public MainChapter patchMainChapter(
            long mainChapterId,
            MainChapterPatchRequest request,
            long actorUserId,
            String requestId
    ) {
        requireMainPatchRequest(request);
        MainChapter chapter = requireMainChapter(mainChapterId);
        String beforeJson = mainChapterSnapshot(chapter);

        String title = request.titleProvided()
                ? normalizeMainTitle(request.title())
                : chapter.getTitle();
        String description = request.descriptionProvided()
                ? normalizeDescription(request.description())
                : chapter.getDescription();
        int displayOrder = request.displayOrderProvided()
                ? requireMainDisplayOrder(request.displayOrder())
                : chapter.getDisplayOrder();
        boolean active = request.activeProvided()
                ? requireMainActive(request.active())
                : chapter.isActive();

        if (chapter.isRequired() && !active) {
            throw invalidMainChapter("필수 대단원은 비활성화할 수 없습니다.");
        }
        validateRequiredFoundationOrder(chapter.isRequired(), displayOrder);

        LocalDateTime now = LocalDateTime.now(clock);
        mainChapterMapper.updateMetadata(
                mainChapterId,
                title,
                description,
                displayOrder,
                active,
                now
        );
        chapter.setTitle(title);
        chapter.setDescription(description);
        chapter.setDisplayOrder(displayOrder);
        chapter.setActive(active);
        chapter.setUpdatedAt(now);

        insertAudit(
                actorUserId,
                "UPDATE",
                MAIN_CHAPTER_ENTITY,
                chapter.getMainChapterId(),
                beforeJson,
                mainChapterSnapshot(chapter),
                requestId,
                now
        );
        return chapter;
    }

    @Transactional
    public SubChapter createSubChapter(
            long mainChapterId,
            SubChapterCreateRequest request,
            long actorUserId,
            String requestId
    ) {
        requireSubCreateRequest(request);
        requireMainChapter(mainChapterId);

        String title = normalizeSubTitle(request.title());
        String description = normalizeDescription(request.description());
        int displayOrder = requireSubDisplayOrder(request.displayOrder());
        ensureSubDisplayOrderAvailable(mainChapterId, displayOrder, null);

        LocalDateTime now = LocalDateTime.now(clock);
        SubChapter chapter = SubChapter.create(
                mainChapterId,
                title,
                description,
                displayOrder,
                true,
                now
        );
        try {
            subChapterMapper.insert(chapter);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ErrorCode.SUB_CHAPTER_ORDER_CONFLICT);
        }
        insertAudit(
                actorUserId,
                "CREATE",
                SUB_CHAPTER_ENTITY,
                chapter.getSubChapterId(),
                null,
                subChapterSnapshot(chapter),
                requestId,
                now
        );
        return chapter;
    }

    @Transactional
    public SubChapter patchSubChapter(
            long subChapterId,
            SubChapterPatchRequest request,
            long actorUserId,
            String requestId
    ) {
        requireSubPatchRequest(request);
        SubChapter chapter = requireSubChapter(subChapterId);
        String beforeJson = subChapterSnapshot(chapter);

        String title = request.titleProvided()
                ? normalizeSubTitle(request.title())
                : chapter.getTitle();
        String description = request.descriptionProvided()
                ? normalizeDescription(request.description())
                : chapter.getDescription();
        int displayOrder = request.displayOrderProvided()
                ? requireSubDisplayOrder(request.displayOrder())
                : chapter.getDisplayOrder();
        boolean active = request.activeProvided()
                ? requireSubActive(request.active())
                : chapter.isActive();

        ensureSubDisplayOrderAvailable(
                chapter.getMainChapterId(),
                displayOrder,
                subChapterId
        );

        LocalDateTime now = LocalDateTime.now(clock);
        try {
            subChapterMapper.updateMetadata(
                    subChapterId,
                    title,
                    description,
                    displayOrder,
                    active,
                    now
            );
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ErrorCode.SUB_CHAPTER_ORDER_CONFLICT);
        }
        chapter.setTitle(title);
        chapter.setDescription(description);
        chapter.setDisplayOrder(displayOrder);
        chapter.setActive(active);
        chapter.setUpdatedAt(now);

        insertAudit(
                actorUserId,
                "UPDATE",
                SUB_CHAPTER_ENTITY,
                chapter.getSubChapterId(),
                beforeJson,
                subChapterSnapshot(chapter),
                requestId,
                now
        );
        return chapter;
    }

    private void requireMainCreateRequest(MainChapterCreateRequest request) {
        if (request == null
                || request.chapterType() == null
                || request.isRequired() == null) {
            throw invalidMainChapter("대단원 생성 필수 필드가 누락되었습니다.");
        }
    }

    private void requireMainPatchRequest(MainChapterPatchRequest request) {
        if (request == null || !request.hasAnyField()) {
            throw invalidMainChapter("수정할 대단원 필드가 없습니다.");
        }
    }

    private void requireSubCreateRequest(SubChapterCreateRequest request) {
        if (request == null) {
            throw invalidSubChapter("소단원 생성 정보가 필요합니다.");
        }
    }

    private void requireSubPatchRequest(SubChapterPatchRequest request) {
        if (request == null || !request.hasAnyField()) {
            throw invalidSubChapter("수정할 소단원 필드가 없습니다.");
        }
    }

    private void validateMainChapterType(
            ChapterType chapterType,
            AssetType assetType,
            boolean required
    ) {
        if (chapterType == ChapterType.FOUNDATION) {
            if (assetType != null || !required) {
                throw invalidMainChapter(
                        "FOUNDATION은 자산 유형이 없고 필수 과정이어야 합니다."
                );
            }
            return;
        }
        if (assetType == null || required) {
            throw invalidMainChapter(
                    "ASSET 대단원에는 자산 유형이 필요하며 필수 과정일 수 없습니다."
            );
        }
    }

    private void validateRequiredFoundationOrder(
            boolean required,
            int displayOrder
    ) {
        if (required && displayOrder != 1) {
            throw invalidMainChapter("필수 포트폴리오 기초 과정의 표시 순서는 1이어야 합니다.");
        }
    }

    private String normalizeMainTitle(String value) {
        return normalizeTitle(value, true);
    }

    private String normalizeSubTitle(String value) {
        return normalizeTitle(value, false);
    }

    private String normalizeTitle(String value, boolean mainChapter) {
        if (!StringUtils.hasText(value)) {
            if (mainChapter) {
                throw invalidMainChapter("단원 제목은 비어 있을 수 없습니다.");
            }
            throw invalidSubChapter("단원 제목은 비어 있을 수 없습니다.");
        }
        String title = value.trim();
        if (title.codePointCount(0, title.length()) > TITLE_MAX_LENGTH) {
            if (mainChapter) {
                throw invalidMainChapter("단원 제목은 100자 이하여야 합니다.");
            }
            throw invalidSubChapter("단원 제목은 100자 이하여야 합니다.");
        }
        return title;
    }

    private String normalizeDescription(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int requireMainDisplayOrder(Integer displayOrder) {
        if (displayOrder == null || displayOrder <= 0) {
            throw invalidMainChapter("표시 순서는 1 이상의 정수여야 합니다.");
        }
        return displayOrder;
    }

    private int requireSubDisplayOrder(Integer displayOrder) {
        if (displayOrder == null || displayOrder <= 0) {
            throw invalidSubChapter("표시 순서는 1 이상의 정수여야 합니다.");
        }
        return displayOrder;
    }

    private boolean requireMainActive(Boolean active) {
        if (active == null) {
            throw invalidMainChapter("is_active는 null일 수 없습니다.");
        }
        return active;
    }

    private boolean requireSubActive(Boolean active) {
        if (active == null) {
            throw invalidSubChapter("is_active는 null일 수 없습니다.");
        }
        return active;
    }

    private void ensureSubDisplayOrderAvailable(
            long mainChapterId,
            int displayOrder,
            Long excludedSubChapterId
    ) {
        if (subChapterMapper.countDisplayOrderConflict(
                mainChapterId,
                displayOrder,
                excludedSubChapterId
        ) > 0) {
            throw new ApiException(ErrorCode.SUB_CHAPTER_ORDER_CONFLICT);
        }
    }

    private MainChapter requireMainChapter(long mainChapterId) {
        MainChapter chapter = mainChapterMapper.findById(mainChapterId);
        if (chapter == null) {
            throw new ApiException(ErrorCode.MAIN_CHAPTER_NOT_FOUND);
        }
        return chapter;
    }

    private SubChapter requireSubChapter(long subChapterId) {
        SubChapter chapter = subChapterMapper.findById(subChapterId);
        if (chapter == null) {
            throw new ApiException(ErrorCode.SUB_CHAPTER_NOT_FOUND);
        }
        return chapter;
    }

    private ApiException invalidMainChapter(String message) {
        return new ApiException(ErrorCode.INVALID_MAIN_CHAPTER, message);
    }

    private ApiException invalidSubChapter(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message);
    }

    private void insertAudit(
            long actorUserId,
            String actionType,
            String entityType,
            Long entityId,
            String beforeJson,
            String afterJson,
            String requestId,
            LocalDateTime createdAt
    ) {
        adminAuditLogMapper.insert(
                actorUserId,
                actionType,
                entityType,
                entityId,
                beforeJson,
                afterJson,
                requestId,
                createdAt
        );
    }

    private String mainChapterSnapshot(MainChapter chapter) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("main_chapter_id", chapter.getMainChapterId());
        snapshot.put("chapter_type", chapter.getChapterType().name());
        snapshot.put(
                "asset_type",
                chapter.getAssetType() == null
                        ? null
                        : chapter.getAssetType().name()
        );
        snapshot.put("title", chapter.getTitle());
        snapshot.put("description", chapter.getDescription());
        snapshot.put("display_order", chapter.getDisplayOrder());
        snapshot.put("is_required", chapter.isRequired());
        snapshot.put("is_active", chapter.isActive());
        return writeAuditJson(snapshot);
    }

    private String subChapterSnapshot(SubChapter chapter) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sub_chapter_id", chapter.getSubChapterId());
        snapshot.put("main_chapter_id", chapter.getMainChapterId());
        snapshot.put("title", chapter.getTitle());
        snapshot.put("description", chapter.getDescription());
        snapshot.put("display_order", chapter.getDisplayOrder());
        snapshot.put(
                "current_content_version_id",
                chapter.getCurrentContentVersionId()
        );
        snapshot.put("is_active", chapter.isActive());
        return writeAuditJson(snapshot);
    }

    private String writeAuditJson(Map<String, Object> snapshot) {
        try {
            return auditObjectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("관리자 감사 로그 JSON 생성에 실패했습니다.", exception);
        }
    }
}
