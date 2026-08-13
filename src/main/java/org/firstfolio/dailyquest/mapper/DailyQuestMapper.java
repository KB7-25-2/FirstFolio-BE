package org.firstfolio.dailyquest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestItem;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyQuestMapper {

    Long findUserIdForUpdate(@Param("userId") long userId);

    DailyQuest findByUserIdAndQuestDate(
            @Param("userId") long userId,
            @Param("questDate") LocalDate questDate
    );

    DailyQuest findByUserIdAndQuestDateForUpdate(
            @Param("userId") long userId,
            @Param("questDate") LocalDate questDate
    );

    DailyQuest findByIdForUpdate(
            @Param("dailyQuestId") long dailyQuestId
    );

    List<DailyQuestItem> findItemsByDailyQuestId(
            @Param("dailyQuestId") long dailyQuestId
    );

    DailyQuestItem findItemByIdAndUserIdForUpdate(
            @Param("dailyQuestItemId") long dailyQuestItemId,
            @Param("userId") long userId
    );

    int countItemsByDailyQuestId(
            @Param("dailyQuestId") long dailyQuestId
    );

    int countAnsweredItemsByDailyQuestId(
            @Param("dailyQuestId") long dailyQuestId
    );

    int insertQuest(DailyQuest dailyQuest);

    int insertItem(DailyQuestItem item);
}
