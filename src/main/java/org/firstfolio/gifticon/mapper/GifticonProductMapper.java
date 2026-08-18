package org.firstfolio.gifticon.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.domain.GifticonProductInventory;

import java.util.List;

@Mapper
public interface GifticonProductMapper {
    void insert(GifticonProduct product);
    GifticonProduct findById(@Param("gifticonProductId") long gifticonProductId);
    List<GifticonProductInventory> findPage(
            @Param("status") String status,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );
    int update(GifticonProduct product);
}
