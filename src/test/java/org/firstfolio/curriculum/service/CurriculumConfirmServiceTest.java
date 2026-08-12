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

class CurriculumConfirmServiceTest {

    private static final long USER_ID = 11L;
    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            8,
            12,
            1,
            2,
            3
    );

    private UserCurriculumMapper userCurriculumMapper;
    private CurriculumDraftService curriculumDraftService;
    private CurriculumConfirmService service;

    @BeforeEach
    void setUp() {
        userCurriculumMapper = mock(UserCurriculumMapper.class);
        curriculumDraftService = mock(CurriculumDraftService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-12T01:02:03Z"),
                ZoneOffset.UTC
        );
        service = new CurriculumConfirmService(
                userCurriculumMapper,
                curriculumDraftService,
                clock
        );
        when(userCurriculumMapper.findUserIdForUpdate(USER_ID))
                .thenReturn(USER_ID);
    }

    @Test
    void confirmsNormalizedDraftInOneBatch() {
        List<CurriculumDraftItem> items = normalizedItems();
        when(curriculumDraftService.editDraft(USER_ID, List.of(3L, 2L)))
                .thenReturn(items);
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of());
        when(userCurriculumMapper.insertAll(
                eq(USER_ID),
                eq(items),
                any(LocalDateTime.class)
        )).thenReturn(items.size());

        List<CurriculumDraftItem> result = service.confirm(
                USER_ID,
                List.of(3L, 2L)
        );

        assertEquals(items, result);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(
                LocalDateTime.class
        );
        verify(userCurriculumMapper).insertAll(
                eq(USER_ID),
                eq(items),
                timeCaptor.capture()
        );
        assertEquals(NOW, timeCaptor.getValue());
    }

    @Test
    void returnsExistingResultForSameConfirmationWithoutInsertingAgain() {
        List<CurriculumDraftItem> items = normalizedItems();
        when(curriculumDraftService.editDraft(USER_ID, List.of(3L, 2L)))
                .thenReturn(items);
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(savedItems(items));

        List<CurriculumDraftItem> result = service.confirm(
                USER_ID,
                List.of(3L, 2L)
        );

        assertEquals(items, result);
        verify(userCurriculumMapper, never()).insertAll(
                eq(USER_ID),
                anyList(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void rejectsDifferentConfirmationAfterCurriculumWasConfirmed() {
        List<CurriculumDraftItem> requested = normalizedItems();
        when(curriculumDraftService.editDraft(USER_ID, List.of(3L, 2L)))
                .thenReturn(requested);
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(savedItems(List.of(
                        requested.get(0),
                        requested.get(2),
                        requested.get(1)
                )));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.confirm(USER_ID, List.of(3L, 2L))
        );

        assertEquals(ErrorCode.CURRICULUM_ALREADY_CONFIRMED,
                exception.getErrorCode());
        verify(userCurriculumMapper, never()).insertAll(
                eq(USER_ID),
                anyList(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void allowsFoundationOnlyCurriculum() {
        List<CurriculumDraftItem> items = List.of(normalizedItems().get(0));
        when(curriculumDraftService.editDraft(USER_ID, List.of()))
                .thenReturn(items);
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of());
        when(userCurriculumMapper.insertAll(USER_ID, items, NOW))
                .thenReturn(1);

        List<CurriculumDraftItem> result = service.confirm(USER_ID, List.of());

        assertEquals(items, result);
        verify(userCurriculumMapper).insertAll(USER_ID, items, NOW);
    }

    @Test
    void doesNotReadOrWriteCurriculumWhenDraftValidationFails() {
        when(curriculumDraftService.editDraft(USER_ID, null)).thenThrow(
                new ApiException(ErrorCode.INVALID_CURRICULUM_SELECTION)
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.confirm(USER_ID, null)
        );

        assertEquals(ErrorCode.INVALID_CURRICULUM_SELECTION,
                exception.getErrorCode());
        verify(userCurriculumMapper, never()).findActiveByUserId(USER_ID);
        verify(userCurriculumMapper, never()).insertAll(
                eq(USER_ID),
                anyList(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void rollsBackWhenBatchInsertCountIsUnexpected() {
        List<CurriculumDraftItem> items = normalizedItems();
        when(curriculumDraftService.editDraft(USER_ID, List.of(3L, 2L)))
                .thenReturn(items);
        when(userCurriculumMapper.findActiveByUserId(USER_ID))
                .thenReturn(List.of());
        when(userCurriculumMapper.insertAll(USER_ID, items, NOW))
                .thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.confirm(USER_ID, List.of(3L, 2L))
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    private List<CurriculumDraftItem> normalizedItems() {
        return List.of(
                new CurriculumDraftItem(
                        1L,
                        "포트폴리오 기초",
                        CurriculumSourceType.FOUNDATION,
                        1,
                        false
                ),
                new CurriculumDraftItem(
                        3L,
                        "채권",
                        CurriculumSourceType.USER_ADDED,
                        2,
                        true
                ),
                new CurriculumDraftItem(
                        2L,
                        "예·적금",
                        CurriculumSourceType.LEVEL_TEST_WRONG,
                        3,
                        true
                )
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
            saved.setConfirmedAt(NOW);
            return saved;
        }).toList();
    }
}
