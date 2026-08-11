package org.firstfolio.reward.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.reward.domain.PointTransaction;
import org.firstfolio.reward.domain.RewardPolicy;

import java.time.LocalDateTime;

@Mapper
public interface QuizRewardMapper {

    RewardPolicy findActivePolicyAt(
            @Param("policyKey") String policyKey,
            @Param("effectiveAt") LocalDateTime effectiveAt
    );

    PointTransaction findTransactionById(
            @Param("pointTransactionId") long pointTransactionId
    );

    int increasePointBalance(
            @Param("userId") long userId,
            @Param("amount") int amount,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    Integer findPointBalance(@Param("userId") long userId);

    int insertTransaction(PointTransaction transaction);
}
