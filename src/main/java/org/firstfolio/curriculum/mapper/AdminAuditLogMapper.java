package org.firstfolio.curriculum.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AdminAuditLogMapper {

    int insert(
            @Param("actorUserId") long actorUserId,
            @Param("actionType") String actionType,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("beforeJson") String beforeJson,
            @Param("afterJson") String afterJson,
            @Param("requestId") String requestId,
            @Param("createdAt") LocalDateTime createdAt
    );
}
