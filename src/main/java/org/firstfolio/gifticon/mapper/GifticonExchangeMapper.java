package org.firstfolio.gifticon.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.gifticon.domain.GifticonCode;
import org.firstfolio.gifticon.domain.GifticonDisclosureData;
import org.firstfolio.gifticon.domain.GifticonOrder;
import org.firstfolio.gifticon.domain.GifticonOrderView;
import org.firstfolio.reward.domain.PointTransaction;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GifticonExchangeMapper {

    Integer findPointBalanceForUpdate(@Param("userId") long userId);
    Integer findPointBalance(@Param("userId") long userId);
    int decreasePointBalance(
            @Param("userId") long userId,
            @Param("amount") int amount,
            @Param("updatedAt") LocalDateTime updatedAt
    );
    void insertPointTransaction(PointTransaction transaction);
    int linkPointTransactionReason(
            @Param("pointTransactionId") long pointTransactionId,
            @Param("gifticonOrderId") long gifticonOrderId
    );
    GifticonCode lockNextAvailableCode(
            @Param("gifticonProductId") long gifticonProductId,
            @Param("now") LocalDateTime now
    );
    GifticonCode lockNextAvailableCodeWaiting(
            @Param("gifticonProductId") long gifticonProductId,
            @Param("now") LocalDateTime now
    );
    int assignCode(@Param("gifticonCodeId") long gifticonCodeId);
    void insertOrder(GifticonOrder order);
    GifticonOrderView findByUserAndIdempotency(
            @Param("userId") long userId,
            @Param("idempotencyKey") String idempotencyKey
    );
    List<GifticonOrderView> findOrdersByUser(
            @Param("userId") long userId,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );
    GifticonOrderView findOrderByUser(
            @Param("userId") long userId,
            @Param("gifticonOrderId") long gifticonOrderId
    );
    GifticonDisclosureData findDisclosureForUpdate(
            @Param("userId") long userId,
            @Param("gifticonOrderId") long gifticonOrderId
    );
    int markFirstDisclosed(
            @Param("gifticonOrderId") long gifticonOrderId,
            @Param("disclosedAt") LocalDateTime disclosedAt
    );
    void insertAccessLog(
            @Param("gifticonOrderId") long gifticonOrderId,
            @Param("actorUserId") long actorUserId,
            @Param("requestId") String requestId,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
