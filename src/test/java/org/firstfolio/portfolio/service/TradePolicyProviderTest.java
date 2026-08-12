package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.policy.domain.SystemPolicy;
import org.firstfolio.policy.service.SystemPolicyReader;
import org.firstfolio.portfolio.domain.TradePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradePolicyProviderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 6, 0);

    /** v3 3.3절 확정값 + D14. 설정 기본값과 같아야 한다. */
    private static final BigDecimal DEFAULT_FEE = new BigDecimal("0.00015");
    private static final BigDecimal DEFAULT_TRANSACTION_TAX = new BigDecimal("0.0020");
    private static final BigDecimal DEFAULT_INCOME_TAX = new BigDecimal("0.154");

    private static final ObjectMapper OBJECT_MAPPER = ApiObjectMapperFactory.create();

    private SystemPolicyReader systemPolicyReader;
    private TradePolicyProvider provider;

    @BeforeEach
    void setUp() {
        systemPolicyReader = mock(SystemPolicyReader.class);

        provider = new TradePolicyProvider(
                systemPolicyReader,
                DEFAULT_FEE,
                DEFAULT_FEE,
                DEFAULT_TRANSACTION_TAX,
                DEFAULT_INCOME_TAX,
                DEFAULT_INCOME_TAX
        );
    }

    /** 저장된 정책이 있는 상황을 만든다. */
    private void givenPolicy(int versionNo, String configJson) {
        SystemPolicy policy = new SystemPolicy();

        policy.setPolicyKey("TRADE");
        policy.setVersionNo(versionNo);
        policy.setConfigJson(configJson);
        policy.setActive(true);

        when(systemPolicyReader.findActive(anyString(), any())).thenReturn(policy);

        try {
            JsonNode config = configJson == null ? null : OBJECT_MAPPER.readTree(configJson);

            when(systemPolicyReader.findActiveConfig(anyString(), any())).thenReturn(config);
        } catch (Exception exception) {
            when(systemPolicyReader.findActiveConfig(anyString(), any())).thenReturn(null);
        }
    }

    private static String fullConfig() {
        return "{"
                + "\"buy_fee_rate\": \"0.0001\","
                + "\"sell_fee_rate\": \"0.0002\","
                + "\"securities_transaction_tax_rate\": \"0.003\","
                + "\"dividend_income_tax_rate\": \"0.11\","
                + "\"interest_income_tax_rate\": \"0.12\""
                + "}";
    }

    // ------------------------------------------------------------- 저장된 정책

    @Test
    @DisplayName("저장된 정책이 있으면 그 값을 쓴다 — 설정 기본값이 아니다")
    void usesStoredPolicyWhenPresent() {
        givenPolicy(3, fullConfig());

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(new BigDecimal("0.0001"), policy.getBuyFeeRate());
        assertEquals(new BigDecimal("0.0002"), policy.getSellFeeRate());
        assertEquals(new BigDecimal("0.003"), policy.getSecuritiesTransactionTaxRate());
        assertEquals(new BigDecimal("0.11"), policy.getDividendIncomeTaxRate());
        assertEquals(new BigDecimal("0.12"), policy.getInterestIncomeTaxRate());
    }

    @Test
    @DisplayName("적용된 정책 버전을 남긴다 — 나중에 과거 거래를 검산해야 한다")
    void keepsPolicyVersion() {
        givenPolicy(3, fullConfig());

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(3, policy.getPolicyVersion());
        assertTrue(policy.isFromStoredPolicy());
    }

    @Test
    @DisplayName("숫자로 담겨 있어도 읽는다 — 문자열이 권장이지만 강제는 아니다")
    void readsNumericJsonValues() {
        givenPolicy(1, "{\"buy_fee_rate\": 0.00025}");

        assertEquals(new BigDecimal("0.00025"), provider.findAt(NOW).getBuyFeeRate());
    }

    // ------------------------------------------------------------- 폴백

    @Test
    @DisplayName("정책이 없으면 설정 기본값으로 돈다 — 거래가 멈추면 안 된다")
    void fallsBackWhenNoPolicy() {
        when(systemPolicyReader.findActive(anyString(), any())).thenReturn(null);

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(DEFAULT_FEE, policy.getBuyFeeRate());
        assertEquals(DEFAULT_INCOME_TAX, policy.getInterestIncomeTaxRate());
        assertNull(policy.getPolicyVersion(), "기본값으로 돌았다는 사실이 드러나야 합니다.");
        assertFalse(policy.isFromStoredPolicy());
    }

    @Test
    @DisplayName("내용을 못 읽으면 전부 기본값이다")
    void fallsBackWhenConfigUnreadable() {
        givenPolicy(2, null);
        when(systemPolicyReader.findActiveConfig(anyString(), any())).thenReturn(null);

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(DEFAULT_FEE, policy.getBuyFeeRate());
        assertNull(policy.getPolicyVersion());
    }

    @Test
    @DisplayName("빠진 항목만 기본값으로 넘어간다 — 오타 하나로 전체가 넘어가면 안 된다")
    void fallsBackPerMissingField() {
        givenPolicy(4, "{\"buy_fee_rate\": \"0.0009\"}");

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(new BigDecimal("0.0009"), policy.getBuyFeeRate(), "있는 값은 그대로");
        assertEquals(DEFAULT_FEE, policy.getSellFeeRate(), "빠진 값만 기본값");
        assertEquals(DEFAULT_INCOME_TAX, policy.getInterestIncomeTaxRate());
        assertEquals(4, policy.getPolicyVersion(), "일부가 넘어가도 정책 버전은 그 정책이다.");
    }

    @Test
    @DisplayName("숫자로 해석되지 않는 값은 기본값으로 넘어간다")
    void fallsBackOnUnparsableValue() {
        givenPolicy(5, "{\"buy_fee_rate\": \"영점영일오\"}");

        assertEquals(DEFAULT_FEE, provider.findAt(NOW).getBuyFeeRate());
    }

    @Test
    @DisplayName("비율 범위를 벗어나면 기본값으로 넘어간다 — 퍼센트로 착각한 값이 그대로 들어가면 자산이 사라진다")
    void rejectsRatesOutsideZeroToOne() {
        givenPolicy(6, "{\"buy_fee_rate\": \"15.4\", \"sell_fee_rate\": \"-0.1\"}");

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(DEFAULT_FEE, policy.getBuyFeeRate(), "1을 넘는 비율은 거부");
        assertEquals(DEFAULT_FEE, policy.getSellFeeRate(), "음수 비율은 거부");
    }

    @Test
    @DisplayName("0과 1은 유효한 비율이다 — 무료 수수료 이벤트와 전액 과세가 경계다")
    void acceptsBoundaryRates() {
        givenPolicy(7, "{\"buy_fee_rate\": \"0\", \"sell_fee_rate\": \"1\"}");

        TradePolicy policy = provider.findAt(NOW);

        assertEquals(BigDecimal.ZERO, policy.getBuyFeeRate().stripTrailingZeros());
        assertEquals(0, policy.getSellFeeRate().compareTo(BigDecimal.ONE));
    }

    // ------------------------------------------------------------- 조회 규약

    @Test
    @DisplayName("TRADE 키와 넘겨받은 시각으로 조회한다")
    void queriesWithTradeKeyAndGivenTime() {
        when(systemPolicyReader.findActive(anyString(), any())).thenReturn(null);

        provider.findAt(NOW);

        org.mockito.Mockito.verify(systemPolicyReader).findActive(TradePolicyProvider.POLICY_KEY, NOW);
    }
}
