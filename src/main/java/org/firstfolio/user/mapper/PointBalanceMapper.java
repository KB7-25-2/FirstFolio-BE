package org.firstfolio.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.user.domain.PointBalanceSnapshot;

@Mapper
public interface PointBalanceMapper {

    PointBalanceSnapshot findByUserId(@Param("userId") long userId);
}
