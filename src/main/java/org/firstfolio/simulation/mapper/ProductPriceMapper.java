package org.firstfolio.simulation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.simulation.domain.ProductPrice;

import java.time.LocalDateTime;
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

    /**
     * {@code from} 이후 기준 시점으로 저장된 가격이 몇 건인지.
     *
     * <p>종가를 이미 남겼는지 확인하는 데 쓴다. <b>{@code generation_key} 유니크 제약만으로는
     * 부족하다</b> — 시간외 거래(16:00~18:00) 중에는 시세 갱신 시각이 계속 바뀌어 같은 날
     * 종가가 여러 벌 쌓인다 (2026-08-10 실측: 90초 만에 15건 중 9건이 새 행으로 저장됨).</p>
     *
     * <p>상품을 가리지 않아 {@code idx_product_prices_latest}를 타지 못한다. 하루 15행씩만
     * 쌓이는 테이블이고 재시작 직후 한 번만 부르므로 문제되지 않는다.</p>
     */
    int countSavedSince(@Param("from") LocalDateTime from);

    /**
     * 새 기준 가격 한 행을 저장한다 (FUNC-040).
     *
     * <p>같은 상품·기준 시각이 이미 있으면 {@code uq_product_prices_product_time}에,
     * 같은 생성 키가 있으면 {@code uq_product_prices_generation_key}에 걸려
     * {@code DuplicateKeyException}이 난다. <b>호출한 쪽이 이를 "건너뜀"으로 처리한다</b> —
     * 배치가 재실행돼도 가격이 중복 생성되지 않아야 하기 때문이다.</p>
     */
    void insert(ProductPrice price);
}
