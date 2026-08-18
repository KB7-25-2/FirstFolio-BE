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
    List<GifticonProductInventory> findMarketPage(
            @Param("category") String category,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );
    GifticonProductInventory findOnSaleInventoryById(
            @Param("gifticonProductId") long gifticonProductId
    );
    int update(GifticonProduct product);
}
