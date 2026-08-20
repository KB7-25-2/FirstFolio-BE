package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuestLeaderboardEntry;
import org.firstfolio.dailyquest.dto.response.DailyQuestLeaderboardResponse;
import org.firstfolio.dailyquest.mapper.DailyQuestLeaderboardMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestLeaderboardQueryServiceTest {

    private static final long USER_ID = 10L;
    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 20);

    private DailyQuestLeaderboardMapper mapper;
    private DailyQuestLeaderboardQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DailyQuestLeaderboardMapper.class);
        service = new DailyQuestLeaderboardQueryService(
                mapper,
                Clock.fixed(
                        Instant.parse("2026-08-20T06:30:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void returnsRealtimePageAndMyRankWithOpaqueNextCursor() {
        when(mapper.findTodayPage(QUEST_DATE, null, null, null, 3))
                .thenReturn(List.of(
                        entry(11L, 1, "금융새싹", 5, completedAt(4)),
                        entry(12L, 2, "채권꿈나무", 4, completedAt(5)),
                        entry(13L, 2, "주식탐험가", 4, completedAt(6))
                ));
        when(mapper.findTodayEntry(QUEST_DATE, USER_ID))
                .thenReturn(entry(
                        USER_ID,
                        4,
                        "나",
                        3,
                        completedAt(7)
                ));

        DailyQuestLeaderboardResponse response = service.getToday(
                USER_ID,
                null,
                2
        );

        assertEquals(QUEST_DATE, response.questDate());
        assertEquals(
                LocalDateTime.of(2026, 8, 20, 6, 30),
                response.calculatedAt()
        );
        assertEquals(2, response.items().size());
        assertEquals(1, response.items().get(0).rank());
        assertEquals("금융새싹", response.items().get(0).nickname());
        assertEquals(5, response.items().get(0).score());
        assertEquals(4, response.myRank().rank());
        assertEquals(3, response.myRank().score());
        assertEquals(
                "2026-08-20:4:"
                        + completedAt(5).toEpochSecond(ZoneOffset.UTC)
                        + ":12",
                decode(response.nextCursor())
        );
    }

    @Test
    void usesScoreAndUserIdFromCursorForNextPage() {
        LocalDateTime cursorCompletedAt = completedAt(5);
        String cursor = encode(
                "2026-08-20:4:"
                        + cursorCompletedAt.toEpochSecond(ZoneOffset.UTC)
                        + ":12"
        );
        when(mapper.findTodayPage(
                QUEST_DATE,
                4,
                cursorCompletedAt,
                12L,
                21
        )).thenReturn(List.of(
                entry(13L, 2, "주식탐험가", 4, completedAt(6))
        ));

        DailyQuestLeaderboardResponse response = service.getToday(
                USER_ID,
                cursor,
                null
        );

        assertEquals(1, response.items().size());
        assertNull(response.nextCursor());
        assertNull(response.myRank());
        verify(mapper).findTodayEntry(QUEST_DATE, USER_ID);
    }

    @Test
    void returnsNullMyRankWhenTodayQuestIsNotCompleted() {
        when(mapper.findTodayPage(QUEST_DATE, null, null, null, 21))
                .thenReturn(List.of());

        DailyQuestLeaderboardResponse response = service.getToday(
                USER_ID,
                null,
                null
        );

        assertEquals(List.of(), response.items());
        assertNull(response.myRank());
        assertNull(response.nextCursor());
    }

    @Test
    void rejectsCursorFromAnotherQuestDate() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getToday(
                        USER_ID,
                        encode(
                                "2026-08-19:5:"
                                        + completedAt(5).toEpochSecond(
                                                ZoneOffset.UTC
                                        )
                                        + ":11"
                        ),
                        20
                )
        );

        assertEquals(
                ErrorCode.INVALID_LEADERBOARD_PAGE,
                exception.getErrorCode()
        );
        verify(mapper, never()).findTodayPage(
                QUEST_DATE,
                null,
                null,
                null,
                21
        );
    }

    @Test
    void rejectsInvalidPageSize() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getToday(USER_ID, null, 101)
        );

        assertEquals(
                ErrorCode.INVALID_LEADERBOARD_PAGE,
                exception.getErrorCode()
        );
    }

    private static DailyQuestLeaderboardEntry entry(
            long userId,
            long rank,
            String nickname,
            int score,
            LocalDateTime completedAt
    ) {
        DailyQuestLeaderboardEntry entry = new DailyQuestLeaderboardEntry();
        entry.setUserId(userId);
        entry.setRankNo(rank);
        entry.setNickname(nickname);
        entry.setScore(score);
        entry.setCompletedAt(completedAt);
        return entry;
    }

    private static LocalDateTime completedAt(int hour) {
        return LocalDateTime.of(2026, 8, 20, hour, 0);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8
        );
    }
}
