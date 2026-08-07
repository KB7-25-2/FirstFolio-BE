package org.firstfolio.simulation.domain;

import java.util.List;

/**
 * 초기 상품 구성의 펀드(ETF) 5종목 (SIMULATION_POLICY_v3 1절: 주식형 2 / 채권형 2 / 혼합형 1).
 *
 * <p>ETF 시세 API는 <b>유형(주식형·채권형·혼합형)을 알려주지 않는다.</b> 기초지수명으로
 * 유추할 수는 있지만 규칙이 불안정해서, 정책이 정한 구성을 내부 매핑으로 명시한다.</p>
 *
 * <p><b>{@code realName}은 내부 전용이다.</b> 사용자 응답에 노출하지 않는다 (FUNC-032/038).</p>
 */
public final class EtfCatalog {

    private static final List<EtfCatalog> ALL = List.of(
            new EtfCatalog("069500", "KODEX 200", FundType.EQUITY),
            new EtfCatalog("229200", "KODEX 코스닥150", FundType.EQUITY),
            new EtfCatalog("273130", "KODEX 종합채권(AA-이상)액티브", FundType.BOND),
            new EtfCatalog("157450", "TIGER 단기통안채", FundType.BOND),
            new EtfCatalog("284430", "KODEX 200미국채혼합50", FundType.MIXED)
    );

    /** 펀드 유형. */
    public enum FundType {

        /** 주식형 */
        EQUITY,
        /** 채권형 */
        BOND,
        /** 혼합형 */
        MIXED
    }

    private final String shortCode;
    private final String realName;
    private final FundType fundType;

    private EtfCatalog(String shortCode, String realName, FundType fundType) {
        this.shortCode = shortCode;
        this.realName = realName;
        this.fundType = fundType;
    }

    public static List<EtfCatalog> all() {
        return ALL;
    }

    public static List<String> allShortCodes() {
        return ALL.stream().map(EtfCatalog::getShortCode).toList();
    }

    public String getShortCode() {
        return shortCode;
    }

    /** 내부 전용 실제 종목명. 사용자 응답에 노출 금지. */
    public String getRealName() {
        return realName;
    }

    public FundType getFundType() {
        return fundType;
    }
}
