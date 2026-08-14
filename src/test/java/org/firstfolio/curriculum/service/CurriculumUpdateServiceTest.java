package org.firstfolio.curriculum.service;

import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.curriculum.domain.UserCurriculumItem;
import org.firstfolio.curriculum.mapper.UserCurriculumMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurriculumUpdateServiceTest {

    private static final long USER_ID = 11L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            14,
            1,
            2,
            3
    );

    private UserCurriculumMapper userCurriculumMapper;
    private CurriculumDraftService curriculumDraftService;
    private CurriculumUpdateService service;

    @BeforeEach
    void setUp() {
        userCurriculumMapper = mock(UserCurriculumMapper.class);
        curriculumDraftService = mock(CurriculumDraftService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T01:02:03Z"),
                ZoneOffset.UTC
        );
        service = new CurriculumUpdateService(
                userCurriculumMapper,
                curriculumDraftService,
                clock
        );
        when(userCurriculumMapper.findUserIdForUpdate(USER_ID))
                .thenReturn(USER_ID);
    }

    @Test
    void replacesActiveCurriculumWithNormalizedItems() {
        List<UserCurriculumItem> existing = savedItems(List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1),
                item(2L, CurriculumSourceType.LEVEL_TEST_WRONG, 2)
        ));
        List<CurriculumDraftItem> updated = List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1),
                item(3L, CurriculumSourceType.USER_ADDED, 2)
        );
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(existing);
        when(curriculumDraftService.editDraft(USER_ID, List.of(3L)))
                .thenReturn(updated);
        when(userCurriculumMapper.markActiveAsRemoved(USER_ID))
                .thenReturn(existing.size());
        when(userCurriculumMapper.upsertAll(USER_ID, updated, NOW))
                .thenReturn(3);

        List<CurriculumDraftItem> result = service.update(
                USER_ID,
                List.of(3L)
        );

        assertEquals(updated, result);
        verify(userCurriculumMapper).markActiveAsRemoved(USER_ID);
        verify(userCurriculumMapper).upsertAll(USER_ID, updated, NOW);
    }

    @Test
    void returnsSameCurriculumWithoutWriting() {
        List<CurriculumDraftItem> requested = List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1),
                item(2L, CurriculumSourceType.LEVEL_TEST_WRONG, 2)
        );
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(savedItems(requested));
        when(curriculumDraftService.editDraft(USER_ID, List.of(2L)))
                .thenReturn(requested);

        assertEquals(requested, service.update(USER_ID, List.of(2L)));
        verify(userCurriculumMapper, never()).markActiveAsRemoved(USER_ID);
        verify(userCurriculumMapper, never()).upsertAll(
                eq(USER_ID),
                anyList(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void allowsFoundationOnlyCurriculum() {
        List<UserCurriculumItem> existing = savedItems(List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1),
                item(2L, CurriculumSourceType.LEVEL_TEST_WRONG, 2)
        ));
        List<CurriculumDraftItem> foundationOnly = List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1)
        );
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(existing);
        when(curriculumDraftService.editDraft(USER_ID, List.of()))
                .thenReturn(foundationOnly);
        when(userCurriculumMapper.markActiveAsRemoved(USER_ID))
                .thenReturn(existing.size());
        when(userCurriculumMapper.upsertAll(USER_ID, foundationOnly, NOW))
                .thenReturn(2);

        assertEquals(
                foundationOnly,
                service.update(USER_ID, List.of())
        );
    }

    @Test
    void rejectsUpdateBeforeInitialConfirmation() {
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.update(USER_ID, List.of(2L))
        );

        assertEquals(ErrorCode.CURRICULUM_NOT_FOUND,
                exception.getErrorCode());
        verify(curriculumDraftService, never()).editDraft(
                eq(USER_ID),
                anyList()
        );
    }

    @Test
    void rollsBackWhenActiveRowsAreNotAllRemoved() {
        List<UserCurriculumItem> existing = savedItems(List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1),
                item(2L, CurriculumSourceType.LEVEL_TEST_WRONG, 2)
        ));
        List<CurriculumDraftItem> updated = List.of(
                item(1L, CurriculumSourceType.FOUNDATION, 1)
        );
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(existing);
        when(curriculumDraftService.editDraft(USER_ID, List.of()))
                .thenReturn(updated);
        when(userCurriculumMapper.markActiveAsRemoved(USER_ID))
                .thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.update(USER_ID, List.of())
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(userCurriculumMapper, never()).upsertAll(
                eq(USER_ID),
                anyList(),
                any(LocalDateTime.class)
        );
    }

    private List<UserCurriculumItem> savedItems(
            List<CurriculumDraftItem> items
    ) {
        return items.stream().map(item -> {
            UserCurriculumItem saved = new UserCurriculumItem();
            saved.setUserId(USER_ID);
            saved.setMainChapterId(item.mainChapterId());
            saved.setDisplayOrder(item.displayOrder());
            saved.setSourceType(item.sourceType());
            saved.setStatus(CurriculumItemStatus.ACTIVE);
            return saved;
        }).toList();
    }

    private CurriculumDraftItem item(
            long mainChapterId,
            CurriculumSourceType sourceType,
            int displayOrder
    ) {
        return new CurriculumDraftItem(
                mainChapterId,
                "대단원 " + mainChapterId,
                sourceType,
                displayOrder,
                sourceType != CurriculumSourceType.FOUNDATION
        );
    }
}
