package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.curriculum.domain.UserCurriculumItem;

import java.util.List;

@Mapper
public interface UserCurriculumMapper {

    List<UserCurriculumItem> findActiveByUserId(
            @Param("userId") long userId
    );
}
