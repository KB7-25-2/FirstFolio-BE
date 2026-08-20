package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.portfolio.domain.PortfolioHolding;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.TradePolicy;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioTransactionMapper;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.FinancialProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 예정 이벤트에 남기는 계산 근거 (FUNC-041).
 *
 * <p>금액 자체는 {@link AssetEventCalculatorTest}가 본다. 여기서 보는 것은
 * <b>"왜 이 금액인지를 데이터에 남겼는가"</b>다. 적금은 근거가 없으면 사용자에게
 * <i>"금리 3%인데 왜 2.4% 예금보다 이자가 적지"</i>만 남는다.</p>
 */
class AssetEventSchedulerTest {

    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();

    private static final BigDecimal THREE_MILLION = new BigDecimal("3000000.00");
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 8, 20, 3, 0);

    /** D14 확정 세율. */
    private static final TradePolicy POLICY = new TradePolicy(
            new BigDecimal("0.00015"),
            new BigDecimal("0.00015"),
            new BigDecimal("0.0020"),
            new BigDecimal("0.154"),
            new BigDecimal("0.154"),
            1
    );

    private PortfolioTransactionMapper transactionMapper;
    private AssetEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        transactionMapper = mock(PortfolioTransactionMapper.class);
        scheduler = new AssetEventScheduler(transactionMapper, new AssetEventCalculator(12));
    }

    @Test
    @DisplayName("적금은 회차 정보를 근거에 남긴다 — 화면이 '매월 N원씩 M회 납입'을 띄우는 자리다")
    void recordsInstallmentBasisForSavings() {
        // 실제 상품: KB일반e-Plus정기적금 24개월 3.20% 정액적립식
        int created = scheduler.schedule(
                product("3.20", 24, "SIMPLE", "FIXED"),
                holding(),
                buy(),
                THREE_MILLION,
                OPENED_AT,
                POLICY
        );

        assertEquals(2, created, "이자 1회 + 만기 1회");

        JsonNode detail = interestDetail();

        assertEquals("INSTALLMENT_INTEREST", detail.get("basis").asText());
        assertEquals(24, detail.get("installment_count").asInt());
        // 3,000,000 ÷ 24 = 125,000
        assertEquals("125000.00", detail.get("monthly_amount").asText());
        assertTrue(
                detail.get("installment_assumption").asText().contains("총 납입액"),
                "전액 선납이라는 단순화를 데이터에 남겨야 나중에 검산할 수 있습니다."
        );
    }

    @Test
    @DisplayName("예금 근거에는 회차 정보가 없다 — 나눠 넣는 상품이 아니다")
    void omitsInstallmentBasisForDeposit() {
        scheduler.schedule(
                product("2.60", 24, "SIMPLE", null),
                holding(),
                buy(),
                THREE_MILLION,
                OPENED_AT,
                POLICY
        );

        JsonNode detail = interestDetail();

        assertEquals("SIMPLE_INTEREST", detail.get("basis").asText());
        assertFalse(detail.has("installment_count"));
        assertFalse(detail.has("monthly_amount"));
    }

    @Test
    @DisplayName("주식은 만기가 없어 만들 일정이 없다")
    void schedulesNothingForStock() {
        FinancialProduct stock = new FinancialProduct();

        stock.setProductId(90L);
        stock.setAssetType(AssetType.STOCK);

        assertEquals(0, scheduler.schedule(
                stock, holding(), buy(), THREE_MILLION, OPENED_AT, POLICY));
    }

    // ------------------------------------------------------------------ 보조

    /** 삽입된 이벤트 중 이자 한 건의 {@code detail_json}. 만기는 근거가 원금 반환뿐이다. */
    private JsonNode interestDetail() {
        ArgumentCaptor<PortfolioTransaction> captor =
                ArgumentCaptor.forClass(PortfolioTransaction.class);

        verify(transactionMapper, atLeastOnce()).insert(captor.capture());

        List<PortfolioTransaction> inserted = captor.getAllValues();
        PortfolioTransaction interest = inserted.stream()
                .filter(event -> event.getTransactionType() == TransactionType.INTEREST)
                .findFirst()
                .orElseThrow(() -> new AssertionError("이자 이벤트가 만들어지지 않았습니다."));

        try {
            return OBJECT_MAPPER.readTree(interest.getDetailJson());
        } catch (Exception exception) {
            throw new AssertionError("계산 근거를 읽지 못했습니다.", exception);
        }
    }

    /**
     * 예·적금 상품 하나.
     *
     * <p>{@code real_terms}는 금액을, {@code simulation_terms}는 시각을 정한다. 필드명이
     * snake_case인 것은 저장된 JSON이 그렇기 때문이다({@code ApiObjectMapperFactory}).</p>
     */
    private static FinancialProduct product(
            String ratePercent,
            int maturityMonths,
            String rateType,
            String reserveType
    ) {
        FinancialProduct product = new FinancialProduct();

        product.setProductId(10L);
        product.setAssetType(AssetType.DEPOSIT_SAVINGS);
        product.setRealTermsJson("{"
                + "\"interest_rate\":" + ratePercent + ","
                + "\"maturity_months\":" + maturityMonths + ","
                + "\"interest_interval\":\"MATURITY\","
                + "\"interest_interval_source\":\"ASSUMED\","
                + "\"interest_rate_type\":\"" + rateType + "\""
                + (reserveType == null ? "" : ",\"reserve_type\":\"" + reserveType + "\"")
                + "}");
        product.setSimulationTermsJson(
                "{\"service_maturity_hours\":" + maturityMonths * 24 + "}");

        return product;
    }

    private static PortfolioHolding holding() {
        PortfolioHolding holding = new PortfolioHolding();

        holding.setHoldingId(8101L);
        holding.setPortfolioId(77L);
        holding.setProductId(10L);
        // 가입 당시 조건. 상품 조건이 나중에 바뀌어도 이 값으로 계산한다.
        holding.setTermsSnapshotJson("{\"service_maturity_hours\":576}");

        return holding;
    }

    private static PortfolioTransaction buy() {
        PortfolioTransaction transaction = new PortfolioTransaction();

        transaction.setPortfolioTransactionId(5001L);

        return transaction;
    }
}
