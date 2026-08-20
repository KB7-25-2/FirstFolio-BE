package org.firstfolio.dailyquest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.dailyquest.domain.DailyQuestLeaderboardEntry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DailyQuestLeaderboardMapper {

    List<DailyQuestLeaderboardEntry> findTodayPage(
            @Param("questDate") LocalDate questDate,
            @Param("cursorScore") Integer cursorScore,
            @Param("cursorCompletedAt") LocalDateTime cursorCompletedAt,
            @Param("cursorUserId") Long cursorUserId,
            @Param("limit") int limit
    );

    DailyQuestLeaderboardEntry findTodayEntry(
            @Param("questDate") LocalDate questDate,
            @Param("userId") long userId
    );
}
