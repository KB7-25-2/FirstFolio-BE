package org.firstfolio.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.policy.domain.SystemPolicy;
import org.firstfolio.policy.service.SystemPolicyReader;
import org.firstfolio.portfolio.domain.TradePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 거래 수수료·세율을 가져온다. <b>저장된 정책이 있으면 그것, 없으면 설정 기본값.</b>
 *
 * <p>SIMULATION_POLICY_v3 3.3절이 <i>"실제 요율은 수시 변동되므로 {@code system_policies}의
 * TRADE 정책 버전으로 관리하고 고정 상수로 하드코딩하지 않는다"</i>고 못박았다.</p>
 *
 * <h3>왜 기본값이 필요한가</h3>
 *
 * <p>{@code system_policies}는 <b>쓰기 경로가 명세에 없다</b> — 정책 관리 API가 없고 행은
 * SQL로 넣는다. 즉 <b>아무도 안 넣은 상태가 정상적으로 존재</b>한다. 그때 거래를 막으면
 * 정책을 넣기 전까지 서비스가 통째로 멈춘다.</p>
 *
 * <p>기본값을 자바 상수가 아니라 {@code application.properties}에 두는 것이 v3의 의도와도 맞는다 —
 * <b>재배포 없이 바꿀 수 있어야 한다</b>는 요구는 지켜지고, 저장된 정책이 있으면 그쪽이 항상 이긴다.</p>
 *
 * <h3>일부만 있어도 돈다</h3>
 *
 * <p>정책 JSON에 키가 빠져 있거나 값이 이상하면 <b>그 항목만</b> 기본값으로 넘어간다.
 * 오타 하나로 전체가 기본값이 되면 어느 요율이 적용됐는지 알기 어렵다.
 * 대신 어떤 키가 넘어갔는지 경고에 남긴다.</p>
 *
 * <p>경고는 <b>같은 상황이 이어지는 동안 한 번만</b> 남긴다 — 거래마다 부르는 자리라
 * 그냥 찍으면 로그가 거래 수만큼 쌓인다.</p>
 */
@Component
public class TradePolicyProvider {

    /** {@code system_policies.policy_key}. */
    public static final String POLICY_KEY = "TRADE";

    private static final BigDecimal MAX_RATE = BigDecimal.ONE;

    private static final Logger log = LogManager.getLogger(TradePolicyProvider.class);

    private final SystemPolicyReader systemPolicyReader;

    private final BigDecimal defaultBuyFeeRate;
    private final BigDecimal defaultSellFeeRate;
    private final BigDecimal defaultSecuritiesTransactionTaxRate;
    private final BigDecimal defaultDividendIncomeTaxRate;
    private final BigDecimal defaultInterestIncomeTaxRate;

    /** 직전에 남긴 경고. 같은 내용이 반복되면 다시 남기지 않는다. */
    private final AtomicReference<String> lastWarning = new AtomicReference<>();

    public TradePolicyProvider(
            SystemPolicyReader systemPolicyReader,
            @Value("${trade.policy.buy-fee-rate:0.00015}") BigDecimal defaultBuyFeeRate,
            @Value("${trade.policy.sell-fee-rate:0.00015}") BigDecimal defaultSellFeeRate,
            @Value("${trade.policy.securities-transaction-tax-rate:0.0020}")
            BigDecimal defaultSecuritiesTransactionTaxRate,
            @Value("${trade.policy.dividend-income-tax-rate:0.154}")
            BigDecimal defaultDividendIncomeTaxRate,
            @Value("${trade.policy.interest-income-tax-rate:0.154}")
            BigDecimal defaultInterestIncomeTaxRate
    ) {
        this.systemPolicyReader = systemPolicyReader;
        this.defaultBuyFeeRate = defaultBuyFeeRate;
        this.defaultSellFeeRate = defaultSellFeeRate;
        this.defaultSecuritiesTransactionTaxRate = defaultSecuritiesTransactionTaxRate;
        this.defaultDividendIncomeTaxRate = defaultDividendIncomeTaxRate;
        this.defaultInterestIncomeTaxRate = defaultInterestIncomeTaxRate;
    }

    /**
     * 그 시점에 적용할 요율.
     *
     * @param at 기준 시각(UTC). 거래 시점이자 자산 이벤트 확정 시점이다
     */
    public TradePolicy findAt(LocalDateTime at) {
        SystemPolicy policy = systemPolicyReader.findActive(POLICY_KEY, at);

        if (policy == null) {
            warnOnce("정책 없음", "TRADE 정책이 없어 설정 기본값으로 계산합니다. system_policies에 행을 넣어 주세요.");

            return defaults(null);
        }

        JsonNode config = systemPolicyReader.findActiveConfig(POLICY_KEY, at);

        if (config == null) {
            warnOnce(
                    "내용 없음 v" + policy.getVersionNo(),
                    "TRADE 정책 내용을 읽지 못해 설정 기본값으로 계산합니다 version=" + policy.getVersionNo()
            );

            return defaults(null);
        }

        List<String> fellBack = new ArrayList<>();

        TradePolicy resolved = new TradePolicy(
                rate(config, "buy_fee_rate", defaultBuyFeeRate, fellBack),
                rate(config, "sell_fee_rate", defaultSellFeeRate, fellBack),
                rate(config, "securities_transaction_tax_rate", defaultSecuritiesTransactionTaxRate, fellBack),
                rate(config, "dividend_income_tax_rate", defaultDividendIncomeTaxRate, fellBack),
                rate(config, "interest_income_tax_rate", defaultInterestIncomeTaxRate, fellBack),
                policy.getVersionNo()
        );

        if (!fellBack.isEmpty()) {
            warnOnce(
                    "일부 누락 v" + policy.getVersionNo() + fellBack,
                    "TRADE 정책에서 읽지 못한 항목이 있어 그 항목만 기본값으로 계산합니다 "
                            + "version=" + policy.getVersionNo() + " 항목=" + fellBack
            );
        }

        return resolved;
    }

    /**
     * JSON에서 비율 하나를 읽는다. <b>읽을 수 없으면 기본값</b>이고 어떤 키였는지 기록한다.
     *
     * <p>값은 문자열로 담기지만 숫자로 담겨도 읽는다 — {@code asText()}가 둘 다 처리한다.
     * 부동소수점을 거치지 않으려고 {@link BigDecimal} 문자열 생성자를 쓴다
     * (금액 계산에 float/double 금지, CLAUDE.md 2절).</p>
     *
     * <p>0 미만이거나 1을 넘으면 <b>비율로 성립하지 않으므로</b> 기본값으로 넘어간다.
     * 100을 넣는 실수(퍼센트로 착각)가 그대로 반영되면 자산이 통째로 사라진다.</p>
     */
    private static BigDecimal rate(
            JsonNode config,
            String field,
            BigDecimal fallback,
            List<String> fellBack
    ) {
        JsonNode node = config.get(field);

        if (node == null || node.isNull()) {
            fellBack.add(field);

            return fallback;
        }

        try {
            BigDecimal value = new BigDecimal(node.asText().trim());

            if (value.signum() < 0 || value.compareTo(MAX_RATE) > 0) {
                fellBack.add(field);

                return fallback;
            }

            return value;
        } catch (NumberFormatException exception) {
            fellBack.add(field);

            return fallback;
        }
    }

    private TradePolicy defaults(Integer policyVersion) {
        return new TradePolicy(
                defaultBuyFeeRate,
                defaultSellFeeRate,
                defaultSecuritiesTransactionTaxRate,
                defaultDividendIncomeTaxRate,
                defaultInterestIncomeTaxRate,
                policyVersion
        );
    }

    /** 같은 상황이 이어지는 동안은 한 번만 남긴다. 상황이 바뀌면 다시 남긴다. */
    private void warnOnce(String signature, String message) {
        if (!signature.equals(lastWarning.getAndSet(signature))) {
            log.warn(message);
        }
    }
}
