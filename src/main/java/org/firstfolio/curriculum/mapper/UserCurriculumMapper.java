package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.curriculum.domain.CurriculumDraftItem;
import org.firstfolio.curriculum.domain.CurriculumOverviewItem;
import org.firstfolio.curriculum.domain.UserCurriculumItem;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserCurriculumMapper {

    Long findUserIdForUpdate(@Param("userId") long userId);

    List<UserCurriculumItem> findActiveByUserId(
            @Param("userId") long userId
    );

    List<CurriculumOverviewItem> findOverviewByUserId(
            @Param("userId") long userId
    );

    int insertAll(
            @Param("userId") long userId,
            @Param("items") List<CurriculumDraftItem> items,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    int markActiveAsRemoved(@Param("userId") long userId);

    int upsertAll(
            @Param("userId") long userId,
            @Param("items") List<CurriculumDraftItem> items,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );
}
