package org.firstfolio.simulation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.simulation.domain.ProductPrice;

@Mapper
public interface ProductPriceMapper {

    /**
     * 가장 최근 기준 가격. 없으면 null이다.
     *
     * <p>가격이 없을 때 임의 값을 만들지 않고 그대로 없음을 알린다 (FUNC-032/036).</p>
     */
    ProductPrice findLatestByProductId(@Param("productId") Long productId);
}
