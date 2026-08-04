package org.firstfolio.simulation.service.collector;

import org.firstfolio.simulation.domain.FinancialProduct;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 원천 데이터 하나를 모의 상품으로 변환한다.
 *
 * <p>자산군마다 데이터 제공처·응답 형태·정규화 규칙이 전부 달라서(예·적금은 금감원 finlife,
 * 채권·ETF는 공공데이터포털, 주식은 토스증권) 한 클래스에 모으면 금방 읽을 수 없게 된다.
 * 제공처가 늘어도 기존 코드를 건드리지 않도록 구현체를 나눈다.</p>
 *
 * <p>수집 결과는 <b>전부 비공개({@code isActive=false})</b>여야 한다. 가명은 관리자가
 * 검토 후 입력하고, 그 전에 공개되면 실제 상품명이 노출된다 (FUNC-038).</p>
 */
public interface ProductCollector {

    /** {@code POST /admin/financial-products/imports}의 {@code source_provider} 값. */
    String sourceProvider();

    /**
     * @param referenceAt 관리자가 지정한 원천 데이터 기준 시점
     * @param now         등록 처리 시각(UTC)
     */
    List<FinancialProduct> collect(LocalDateTime referenceAt, LocalDateTime now);
}
