package org.firstfolio.gifticon.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.gifticon.domain.GifticonCode;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GifticonCodeMapper {
    void insert(GifticonCode code);
    GifticonCode findById(@Param("gifticonCodeId") long gifticonCodeId);
    List<GifticonCode> findPage(
            @Param("gifticonProductId") long gifticonProductId,
            @Param("status") String status,
            @Param("expiresBefore") LocalDateTime expiresBefore,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );
    int markVoidIfAvailable(@Param("gifticonCodeId") long gifticonCodeId);
}
