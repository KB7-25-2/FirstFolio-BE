package org.firstfolio.portfolio.service;

import org.firstfolio.portfolio.domain.AssetAllocation;
import org.firstfolio.portfolio.domain.HoldingValuation;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioValuation;
import org.firstfolio.portfolio.domain.ValuationBasis;
import org.firstfolio.portfolio.mapper.PortfolioHoldingMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.ProductPrice;
import org.firstfolio.simulation.service.CurrentPriceReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 포트폴리오 평가액 계산 (FUNC-036).
 *
 * <p>포트폴리오 상세 조회(FUNC-034)와 홈 화면이 같은 숫자를 보여야 하므로 계산은 여기 한 곳에만 둔다.
 * 거래 체결·자산 이벤트 반영 뒤 다시 평가할 때도 이 서비스를 부른다.</p>
 *
 * <h3>자산군별 평가 규칙</h3>
 *
 * <table>
 *   <tr><th>자산군</th><th>평가액</th><th>이유</th></tr>
 *   <tr>
 *     <td>주식·펀드 (매수형)</td>
 *     <td>보유 수량 × 마지막 유효 기준 가격</td>
 *     <td>시세가 오르내리는 상품이다. 가격은 {@link CurrentPriceReader}에서 읽는다 —
 *         체결가와 같은 자리다 (FUNC-040).</td>
 *   </tr>
 *   <tr>
 *     <td>예·적금·채권 (가입형)</td>
 *     <td>투입 원금</td>
 *     <td>시세가 없다. 이자는 지급 시점에 이벤트로 <b>현금</b>에 더해지므로
 *         (FUNC-041) 여기서 또 얹으면 이중 계산이 된다.</td>
 *   </tr>
 * </table>
 *
 * <h3>금액 계산</h3>
 *
 * <p>모든 금액은 {@link BigDecimal}이고 소수점 둘째 자리에서 반올림한다.
 * 부동소수점 타입은 쓰지 않는다 (FUNC-036 예외/제한사항).</p>
 */
@Service
public class PortfolioValuationService {

    /** 금액 자릿수. {@code DECIMAL(19, 2)} 컬럼과 맞춘다. */
    private static final int MONEY_SCALE = 2;

    /** 비율 자릿수. API_DOCS 응답 예시의 {@code 33.38}과 맞춘다. */
    private static final int RATIO_SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioHoldingMapper holdingMapper;
    private final CurrentPriceReader priceReader;

    public PortfolioValuationService(
            PortfolioHoldingMapper holdingMapper,
            CurrentPriceReader priceReader
    ) {
        this.holdingMapper = holdingMapper;
        this.priceReader = priceReader;
    }

    /**
     * 현금·보유자산 평가액·총자산·손익·자산군 비중을 한 번에 계산한다.
     *
     * @param portfolio 평가 대상. 호출한 쪽이 소유자 확인을 마친 상태여야 한다
     */
    @Transactional(readOnly = true)
    public PortfolioValuation valuate(Portfolio portfolio) {
        if (portfolio == null || portfolio.getPortfolioId() == null) {
            throw new IllegalArgumentException("평가할 포트폴리오가 필요합니다.");
        }

        LocalDateTime valuedAt = LocalDateTime.now(ZoneOffset.UTC);

        List<PortfolioHolding> holdings =
                holdingMapper.findActiveByPortfolioId(portfolio.getPortfolioId());
        Map<Long, ProductPrice> prices = latestPrices(holdings);

        List<HoldingValuation> valuations = new ArrayList<>();
        BigDecimal holdingsValue = money(BigDecimal.ZERO);

        for (PortfolioHolding holding : holdings) {
            HoldingValuation valuation = valuate(holding, prices.get(holding.getProductId()));

            valuations.add(valuation);
            holdingsValue = holdingsValue.add(valuation.getValuationAmount());
        }

        BigDecimal cashBalance = money(nullToZero(portfolio.getCashBalance()));
        BigDecimal totalAssets = cashBalance.add(holdingsValue);
        BigDecimal initialAmount = money(nullToZero(portfolio.getInitialAmount()));
        BigDecimal profitLoss = totalAssets.subtract(initialAmount);

        return new PortfolioValuation(
                portfolio,
                valuations,
                allocate(valuations, totalAssets),
                cashBalance,
                holdingsValue,
                totalAssets,
                profitLoss,
                rate(profitLoss, initialAmount),
                valuedAt
        );
    }

    /**
     * 보유 하나의 평가액.
     *
     * <p>기준 가격이 필요한데 없으면 매입 원금을 그대로 두고 {@code PRICE_UNAVAILABLE}로 표시한다.
     * 없는 가격을 만들어 내지 않는다 (FUNC-036).</p>
     */
    private HoldingValuation valuate(PortfolioHolding holding, ProductPrice price) {
        BigDecimal principal = money(nullToZero(holding.getPrincipalAmount()));

        if (isPriceBased(holding.getProductAssetType())) {
            if (price == null || price.getPrice() == null) {
                return new HoldingValuation(holding, principal, ValuationBasis.PRICE_UNAVAILABLE, null);
            }

            BigDecimal amount = money(nullToZero(holding.getQuantity()).multiply(price.getPrice()));

            return new HoldingValuation(
                    holding,
                    amount,
                    ValuationBasis.MARKET_PRICE,
                    price.getReferenceAt()
            );
        }

        return new HoldingValuation(holding, principal, ValuationBasis.PRINCIPAL, null);
    }

    /**
     * 자산군별 평가액과 비중. 보유가 있는 자산군만 담고, 순서는 {@link AssetType} 선언 순서다.
     *
     * <p>비중의 분모는 총자산이라 합이 100%가 되지 않는다. 나머지가 현금 비중이다.</p>
     */
    private List<AssetAllocation> allocate(List<HoldingValuation> valuations, BigDecimal totalAssets) {
        Map<AssetType, BigDecimal> sums = new EnumMap<>(AssetType.class);

        for (HoldingValuation valuation : valuations) {
            AssetType assetType = valuation.getHolding().getProductAssetType();

            if (assetType == null) {
                continue;
            }

            sums.merge(assetType, valuation.getValuationAmount(), BigDecimal::add);
        }

        List<AssetAllocation> allocations = new ArrayList<>();

        for (AssetType assetType : AssetType.values()) {
            BigDecimal sum = sums.get(assetType);

            if (sum == null) {
                continue;
            }

            allocations.add(new AssetAllocation(assetType, sum, rate(sum, totalAssets)));
        }

        return allocations;
    }

    /**
     * 시세로 평가하는 보유의 기준 가격.
     *
     * <p>{@link CurrentPriceReader}를 거친다 — <b>체결가와 같은 자리에서 읽어야</b> 화면의
     * 평가액과 실제 체결 금액이 갈라지지 않는다. 빈 목록·캐시 미스 처리는 그쪽이 맡는다.</p>
     */
    private Map<Long, ProductPrice> latestPrices(List<PortfolioHolding> holdings) {
        Set<Long> productIds = new LinkedHashSet<>();

        for (PortfolioHolding holding : holdings) {
            if (isPriceBased(holding.getProductAssetType()) && holding.getProductId() != null) {
                productIds.add(holding.getProductId());
            }
        }

        return priceReader.readAll(productIds);
    }

    /**
     * 기준 가격으로 평가하는 자산군인지.
     *
     * <p>{@link AssetType#isTimeCompressed()}의 반대다. 만기가 있어 압축하는 상품(예·적금·채권)은
     * 원금으로 평가하고, 만기가 없는 상품(주식·펀드)은 시세로 평가한다. 두 구분이 같은 경계를
     * 쓰는 것은 우연이 아니라 "가입형/매수형"이라는 하나의 성격 차이에서 나온다.</p>
     */
    private static boolean isPriceBased(AssetType assetType) {
        return assetType != null && !assetType.isTimeCompressed();
    }

    /** {@code value / base × 100}. 분모가 0이면 0%로 둔다 (0으로 나누지 않는다). */
    private static BigDecimal rate(BigDecimal value, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }

        return value.multiply(HUNDRED).divide(base, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
