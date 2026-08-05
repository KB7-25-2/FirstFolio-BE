package org.firstfolio.simulation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.simulation.domain.ProductPrice;

import java.util.List;

@Mapper
public interface ProductPriceMapper {

    /**
     * 가장 최근 기준 가격. 없으면 null이다.
     *
     * <p>가격이 없을 때 임의 값을 만들지 않고 그대로 없음을 알린다 (FUNC-032/036).</p>
     */
    ProductPrice findLatestByProductId(@Param("productId") Long productId);

    /**
     * 여러 상품의 최근 기준 가격을 한 번에 읽는다. 가격이 없는 상품은 결과에서 빠진다.
     *
     * <p>포트폴리오 평가는 보유 상품 수만큼 가격이 필요하다. 상품마다 조회하면
     * 보유가 늘어날수록 질의도 늘어나므로 한 번에 읽는다 (FUNC-036).</p>
     *
     * @param productIds 비어 있으면 호출하지 않는다 (MyBatis foreach가 빈 IN 절을 만든다)
     */
    List<ProductPrice> findLatestByProductIds(@Param("productIds") List<Long> productIds);
}
