package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.mapper.ProductPriceMapper;
import org.springframework.stereotype.Component;

/**
 * 체결·평가에 쓰는 <b>현재가를 읽는 단 하나의 자리</b>.
 *
 * <p>지금은 저장된 최신 가격을 그대로 돌려준다. 주문마다 외부 API를 부르지 않는 이유는
 * Rate Limit·제공처 장애·IP 화이트리스트에 <b>거래 기능 전체가 묶이기 때문</b>이다.
 * v3 3.2절의 "가격 확정 시점: 주문 시점"은 <i>그 시점의 값으로 금액을 확정한다</i>는 뜻이지
 * <i>그때 외부를 호출하라</i>는 뜻이 아니다.</p>
 *
 * <h3>이 클래스가 따로 있는 이유</h3>
 *
 * <p>가격 조회 경로를 한 곳에 모아 두려는 것이다. 장중 실시간 가격은 <b>메모리 캐시</b>로 들고
 * 종가만 DB에 저장하기로 했는데(2026-08-07 확정), 그때 <b>이 클래스 안만 바꾸면</b> 거래·평가가
 * 함께 따라온다. 캐시·스케줄러는 별도 이슈다.</p>
 *
 * <p><b>TODO: 평가({@code PortfolioValuationService})는 아직 매퍼를 직접 읽는다.</b>
 * 캐시를 도입할 때 함께 이 클래스로 옮겨야 한다 — 그러지 않으면 화면의 평가액은 종가,
 * 체결가는 실시간이 되어 사용자가 보는 값이 어긋난다.</p>
 */
@Component
public class CurrentPriceReader {

    private final ProductPriceMapper productPriceMapper;

    public CurrentPriceReader(ProductPriceMapper productPriceMapper) {
        this.productPriceMapper = productPriceMapper;
    }

    /**
     * 마지막 유효 기준 가격. <b>없으면 null이다.</b>
     *
     * <p>없는 가격을 만들어 내지 않는다 (FUNC-036). 거래는 이 경우 거부해야 한다 —
     * 임의 값으로 체결하면 사용자 자산이 사실과 달라진다.</p>
     */
    public ProductPrice read(Long productId) {
        return productPriceMapper.findLatestByProductId(productId);
    }
}
