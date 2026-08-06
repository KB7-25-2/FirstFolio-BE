package org.firstfolio.curriculum.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterMetadataServiceTest {

    private static final long ACTOR_ID = 900L;
    private static final String REQUEST_ID = "req-chapter-test";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 6, 0);

    private MainChapterMapper mainChapterMapper;
    private SubChapterMapper subChapterMapper;
    private AdminAuditLogMapper auditLogMapper;
    private ChapterMetadataService service;

    @BeforeEach
    void setUp() {
        mainChapterMapper = mock(MainChapterMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        auditLogMapper = mock(AdminAuditLogMapper.class);
        service = new ChapterMetadataService(
                mainChapterMapper,
                subChapterMapper,
                auditLogMapper,
                Clock.fixed(Instant.parse("2026-08-06T06:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void delegatesMainChapterFiltersToMapper() {
        List<MainChapter> chapters = List.of(mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.BOND,
                false,
                true
        ));
        when(mainChapterMapper.findAll(ChapterType.ASSET, true))
                .thenReturn(chapters);

        assertSame(
                chapters,
                service.getAllMainChapters(ChapterType.ASSET, true)
        );
    }

    @Test
    void createsActiveAssetMainChapterAndWritesAuditLog() {
        doAnswer(invocation -> {
            MainChapter chapter = invocation.getArgument(0);
            chapter.setMainChapterId(2L);
            return 1;
        }).when(mainChapterMapper).insert(any(MainChapter.class));

        MainChapter created = service.createMainChapter(
                new MainChapterCreateRequest(
                        ChapterType.ASSET,
                        AssetType.DEPOSIT_SAVINGS,
                        " 예·적금 ",
                        " 예금과 적금의 기초 ",
                        2,
                        false
                ),
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(2L, created.getMainChapterId());
        assertEquals("예·적금", created.getTitle());
        assertEquals("예금과 적금의 기초", created.getDescription());
        assertFalse(created.isRequired());
        assertTrue(created.isActive());
        assertEquals(NOW, created.getCreatedAt());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("CREATE"),
                eq("MAIN_CHAPTER"),
                eq(2L),
                isNull(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsMainChapterRequiredFlagThatDoesNotMatchType() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createMainChapter(
                        new MainChapterCreateRequest(
                                ChapterType.ASSET,
                                AssetType.BOND,
                                "채권",
                                null,
                                2,
                                true
                        ),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.INVALID_MAIN_CHAPTER, exception.getErrorCode());
        verify(mainChapterMapper, never()).insert(any());
    }

    @Test
    void rejectsSecondActiveFoundation() {
        when(mainChapterMapper.countActiveByChapterType(ChapterType.FOUNDATION))
                .thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createMainChapter(
                        new MainChapterCreateRequest(
                                ChapterType.FOUNDATION,
                                null,
                                "포트폴리오 기초",
                                null,
                                1,
                                true
                        ),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.FOUNDATION_CONFLICT, exception.getErrorCode());
    }

    @Test
    void patchesOnlyProvidedMainChapterFieldsAndRecordsBeforeAfter() {
        MainChapter chapter = mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.BOND,
                false,
                true
        );
        chapter.setDescription("기존 설명");
        when(mainChapterMapper.findById(2L)).thenReturn(chapter);
        MainChapterPatchRequest request = new MainChapterPatchRequest();
        request.setTitle(" 채권의 이해 ");
        request.setDescription(null);

        MainChapter updated = service.patchMainChapter(
                2L,
                request,
                ACTOR_ID,
                REQUEST_ID
        );

        assertSame(chapter, updated);
        assertEquals("채권의 이해", updated.getTitle());
        assertNull(updated.getDescription());
        assertEquals(2, updated.getDisplayOrder());
        assertTrue(updated.isActive());

        ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("UPDATE"),
                eq("MAIN_CHAPTER"),
                eq(2L),
                before.capture(),
                after.capture(),
                eq(REQUEST_ID),
                eq(NOW)
        );
        assertTrue(before.getValue().contains("\"title\":\"대단원\""));
        assertTrue(after.getValue().contains("\"title\":\"채권의 이해\""));
    }

    @Test
    void rejectsDeactivatingRequiredFoundation() {
        MainChapter foundation = mainChapter(
                1L,
                ChapterType.FOUNDATION,
                null,
                true,
                true
        );
        when(mainChapterMapper.findById(1L)).thenReturn(foundation);
        MainChapterPatchRequest request = new MainChapterPatchRequest();
        request.setActive(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.patchMainChapter(
                        1L,
                        request,
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.INVALID_MAIN_CHAPTER, exception.getErrorCode());
        verify(mainChapterMapper, never()).updateMetadata(
                anyLong(), any(), any(), anyInt(), anyBoolean(), any()
        );
    }

    @Test
    void createsActiveSubChapterAndWritesAuditLog() {
        when(mainChapterMapper.findById(2L)).thenReturn(mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.DEPOSIT_SAVINGS,
                false,
                true
        ));
        doAnswer(invocation -> {
            SubChapter chapter = invocation.getArgument(0);
            chapter.setSubChapterId(101L);
            return 1;
        }).when(subChapterMapper).insert(any(SubChapter.class));

        SubChapter created = service.createSubChapter(
                2L,
                new SubChapterCreateRequest(
                        " 예금의 이해 ",
                        " 기본 개념 ",
                        1
                ),
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(101L, created.getSubChapterId());
        assertEquals("예금의 이해", created.getTitle());
        assertTrue(created.isActive());
        assertNull(created.getCurrentContentVersionId());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("CREATE"),
                eq("SUB_CHAPTER"),
                eq(101L),
                isNull(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsDuplicatedSubChapterOrderWithSpecifiedErrorCode() {
        when(mainChapterMapper.findById(2L)).thenReturn(mainChapter(
                2L,
                ChapterType.ASSET,
                AssetType.BOND,
                false,
                true
        ));
        when(subChapterMapper.countDisplayOrderConflict(2L, 1, null))
                .thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createSubChapter(
                        2L,
                        new SubChapterCreateRequest("채권 기초", null, 1),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(
                ErrorCode.SUB_CHAPTER_ORDER_CONFLICT,
                exception.getErrorCode()
        );
    }

    @Test
    void patchesOnlyProvidedSubChapterFields() {
        SubChapter chapter = SubChapter.create(
                2L,
                "기존 제목",
                "기존 설명",
                1,
                true,
                NOW.minusDays(1)
        );
        chapter.setSubChapterId(101L);
        when(subChapterMapper.findById(101L)).thenReturn(chapter);
        SubChapterPatchRequest request = new SubChapterPatchRequest();
        request.setTitle("새 제목");

        SubChapter updated = service.patchSubChapter(
                101L,
                request,
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals("새 제목", updated.getTitle());
        assertEquals("기존 설명", updated.getDescription());
        assertEquals(1, updated.getDisplayOrder());
        assertTrue(updated.isActive());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("UPDATE"),
                eq("SUB_CHAPTER"),
                eq(101L),
                anyString(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsSubChapterForUnknownMainChapter() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.createSubChapter(
                        999L,
                        new SubChapterCreateRequest("소단원", null, 1),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.MAIN_CHAPTER_NOT_FOUND, exception.getErrorCode());
    }

    private MainChapter mainChapter(
            long id,
            ChapterType chapterType,
            AssetType assetType,
            boolean required,
            boolean active
    ) {
        MainChapter chapter = MainChapter.create(
                chapterType,
                assetType,
                "대단원",
                null,
                Math.toIntExact(id),
                required,
                active,
                NOW.minusDays(1)
        );
        chapter.setMainChapterId(id);
        return chapter;
    }
}
