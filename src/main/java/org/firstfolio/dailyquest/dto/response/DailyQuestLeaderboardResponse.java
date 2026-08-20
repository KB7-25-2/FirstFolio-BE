package org.firstfolio.dailyquest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.dailyquest.domain.DailyQuestLeaderboardEntry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DailyQuestLeaderboardResponse(
        @Schema(description = "한국 시간 기준 퀘스트 날짜", example = "2026-08-20")
        LocalDate questDate,
        @Schema(description = "UTC 기준 실시간 집계 시각", example = "2026-08-20T06:30:00Z")
        LocalDateTime calculatedAt,
        List<ItemResponse> items,
        MyRankResponse myRank,
        String nextCursor
) {

    public record ItemResponse(
            long rank,
            String nickname,
            int score
    ) {
        public static ItemResponse from(DailyQuestLeaderboardEntry entry) {
            return new ItemResponse(
                    entry.getRankNo(),
                    entry.getNickname(),
                    entry.getScore()
            );
        }
    }

    public record MyRankResponse(
            long rank,
            int score
    ) {
        public static MyRankResponse from(DailyQuestLeaderboardEntry entry) {
            return entry == null
                    ? null
                    : new MyRankResponse(entry.getRankNo(), entry.getScore());
        }
    }
}
