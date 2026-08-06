package org.firstfolio.simulation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;

import java.util.List;

@Mapper
public interface FinancialProductMapper {

    /**
     * 같은 원천 상품이 이미 등록돼 있는지 확인한다. 수집을 다시 돌려도 중복 등록되지 않게 한다.
     */
    FinancialProduct findBySource(
            @Param("sourceProvider") String sourceProvider,
            @Param("sourceProductCode") String sourceProductCode
    );

    FinancialProduct findById(@Param("productId") Long productId);

    /**
     * 공개 상품만 조회한다 (사용자 API용).
     */
    FinancialProduct findActiveById(@Param("productId") Long productId);

    void insert(FinancialProduct product);

    /**
     * 관리자 목록. {@code assetType}/{@code active}가 null이면 해당 조건을 적용하지 않는다.
     * {@code cursor}는 마지막으로 읽은 product_id다.
     */
    List<FinancialProduct> findPage(
            @Param("assetType") AssetType assetType,
            @Param("active") Boolean active,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );

    /**
     * 관리자 부분 수정. null인 필드는 바꾸지 않는다.
     */
    int updateEditableFields(FinancialProduct product);

    /**
     * 가격 갱신 대상 상품 (FUNC-040).
     *
     * <p>기준 가격으로 평가하는 자산군(주식·펀드)의 공개 상품만 고른다. 비공개 상품은 사용자가
     * 보유할 수 없으므로 가격을 쌓을 이유가 없다.</p>
     *
     * @param productIds null이면 해당 자산군 전체. <b>빈 목록을 넘기지 않는다</b> —
     *                   {@code IN ()}이 만들어져 SQL 오류가 난다. 호출하는 쪽에서 null로 바꾼다
     */
    List<FinancialProduct> findPriceTargets(
            @Param("assetTypes") List<AssetType> assetTypes,
            @Param("productIds") List<Long> productIds
    );
}
