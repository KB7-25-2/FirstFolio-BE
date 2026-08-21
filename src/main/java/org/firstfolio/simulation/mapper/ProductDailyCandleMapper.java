package org.firstfolio.simulation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.simulation.domain.ProductDailyCandle;

import java.util.List;

@Mapper
public interface ProductDailyCandleMapper {

    List<ProductDailyCandle> findRecentByProductId(
            @Param("productId") Long productId,
            @Param("count") int count
    );

    ProductDailyCandle findLatestByProductId(@Param("productId") Long productId);

    List<ProductDailyCandle> findLatestByProductIds(@Param("productIds") List<Long> productIds);

    List<Long> findProductIdsWithCandles(@Param("productIds") List<Long> productIds);

    void upsertAll(@Param("candles") List<ProductDailyCandle> candles);
}
