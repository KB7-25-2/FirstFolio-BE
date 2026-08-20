package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestLeaderboardEntry;
import org.firstfolio.dailyquest.dto.response.DailyQuestLeaderboardResponse;
import org.firstfolio.dailyquest.mapper.DailyQuestLeaderboardMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Service
public class DailyQuestLeaderboardQueryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final DailyQuestLeaderboardMapper leaderboardMapper;
    private final Clock clock;

    public DailyQuestLeaderboardQueryService(
            DailyQuestLeaderboardMapper leaderboardMapper,
            Clock clock
    ) {
        this.leaderboardMapper = leaderboardMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyQuestLeaderboardResponse getToday(
            long userId,
            String cursor,
            Integer size
    ) {
        if (userId <= 0) {
            throw invalidPage();
        }

        Instant calculatedInstant = clock.instant();
        LocalDate questDate = LocalDate.ofInstant(
                calculatedInstant,
                SERVICE_ZONE
        );
        int pageSize = pageSize(size);
        LeaderboardCursor parsedCursor = parseCursor(cursor, questDate);

        List<DailyQuestLeaderboardEntry> found = leaderboardMapper
                .findTodayPage(
                        questDate,
                        parsedCursor == null ? null : parsedCursor.score(),
                        parsedCursor == null
                                ? null
                                : parsedCursor.completedAt(),
                        parsedCursor == null ? null : parsedCursor.userId(),
                        pageSize + 1
                );
        validateEntries(found);

        boolean hasNext = found.size() > pageSize;
        List<DailyQuestLeaderboardEntry> page = List.copyOf(
                hasNext ? found.subList(0, pageSize) : found
        );
        String nextCursor = hasNext
                ? encodeCursor(questDate, page.get(page.size() - 1))
                : null;

        DailyQuestLeaderboardEntry myEntry = leaderboardMapper
                .findTodayEntry(questDate, userId);
        if (myEntry != null) {
            validatePresentEntry(myEntry);
        }

        return new DailyQuestLeaderboardResponse(
                questDate,
                LocalDateTime.ofInstant(calculatedInstant, ZoneOffset.UTC),
                page.stream()
                        .map(DailyQuestLeaderboardResponse.ItemResponse::from)
                        .toList(),
                DailyQuestLeaderboardResponse.MyRankResponse.from(myEntry),
                nextCursor
        );
    }

    private static int pageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw invalidPage();
        }
        return size;
    }

    private static LeaderboardCursor parseCursor(
            String cursor,
            LocalDate questDate
    ) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor.trim()),
                    StandardCharsets.UTF_8
            );
            String[] parts = decoded.split(":", -1);
            if (parts.length != 4) {
                throw invalidPage();
            }
            LocalDate cursorDate = LocalDate.parse(parts[0]);
            int score = Integer.parseInt(parts[1]);
            long completedEpochSecond = Long.parseLong(parts[2]);
            LocalDateTime completedAt = LocalDateTime.ofEpochSecond(
                    completedEpochSecond,
                    0,
                    ZoneOffset.UTC
            );
            long userId = Long.parseLong(parts[3]);
            if (!questDate.equals(cursorDate)
                    || score < 0
                    || score > DailyQuest.TOTAL_QUESTION_COUNT
                    || !questDate.equals(LocalDate.ofInstant(
                            completedAt.toInstant(ZoneOffset.UTC),
                            SERVICE_ZONE
                    ))
                    || userId <= 0) {
                throw invalidPage();
            }
            return new LeaderboardCursor(score, completedAt, userId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidPage();
        }
    }

    private static String encodeCursor(
            LocalDate questDate,
            DailyQuestLeaderboardEntry entry
    ) {
        String value = questDate + ":" + entry.getScore() + ":"
                + entry.getCompletedAt().toEpochSecond(ZoneOffset.UTC) + ":"
                + entry.getUserId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void validateEntries(
            List<DailyQuestLeaderboardEntry> entries
    ) {
        if (entries == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        entries.forEach(
                DailyQuestLeaderboardQueryService::validatePresentEntry
        );
    }

    private static void validatePresentEntry(
            DailyQuestLeaderboardEntry entry
    ) {
        if (entry == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        if (entry.getUserId() <= 0
                || entry.getRankNo() <= 0
                || entry.getNickname() == null
                || entry.getNickname().isBlank()
                || entry.getScore() < 0
                || entry.getScore() > DailyQuest.TOTAL_QUESTION_COUNT
                || entry.getCompletedAt() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static ApiException invalidPage() {
        return new ApiException(ErrorCode.INVALID_LEADERBOARD_PAGE);
    }

    private record LeaderboardCursor(
            int score,
            LocalDateTime completedAt,
            long userId
    ) {
    }
}
