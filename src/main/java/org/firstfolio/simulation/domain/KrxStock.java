package org.firstfolio.simulation.domain;

import java.util.List;

/**
 * 초기 상품 구성의 주식 10종목 (SIMULATION_POLICY_v3 2.2절 확정).
 *
 * <p>토스증권 시세 API는 종목코드와 가격만 돌려주고 <b>종목명을 주지 않는다.</b>
 * 그래서 실제 종목명·산업군·시장 구분을 내부 매핑으로 들고 있어야 한다.</p>
 *
 * <p><b>{@code realName}은 내부 전용이다.</b> 사용자 응답에 절대 노출하지 않는다
 * (FUNC-032/038). 관리자 응답의 {@code source_product_name}으로만 제공한다.</p>
 */
public final class KrxStock {

    /** 반도체·방산·제약·엔터·조선 각 2종목. */
    private static final List<KrxStock> ALL = List.of(
            new KrxStock("005930", "삼성전자", "반도체", "KOSPI"),
            new KrxStock("000660", "SK하이닉스", "반도체", "KOSPI"),
            new KrxStock("012450", "한화에어로스페이스", "방산", "KOSPI"),
            new KrxStock("064350", "현대로템", "방산", "KOSPI"),
            new KrxStock("207940", "삼성바이오로직스", "제약", "KOSPI"),
            new KrxStock("068270", "셀트리온", "제약", "KOSPI"),
            new KrxStock("352820", "하이브", "엔터", "KOSPI"),
            new KrxStock("035900", "JYP Ent.", "엔터", "KOSDAQ"),
            new KrxStock("042660", "한화오션", "조선", "KOSPI"),
            new KrxStock("329180", "HD현대중공업", "조선", "KOSPI")
    );

    private final String symbol;
    private final String realName;
    private final String sector;
    private final String market;

    private KrxStock(String symbol, String realName, String sector, String market) {
        this.symbol = symbol;
        this.realName = realName;
        this.sector = sector;
        this.market = market;
    }

    public static List<KrxStock> all() {
        return ALL;
    }

    public static List<String> allSymbols() {
        return ALL.stream().map(KrxStock::getSymbol).toList();
    }

    public String getSymbol() {
        return symbol;
    }

    /** 내부 전용 실제 종목명. 사용자 응답에 노출 금지. */
    public String getRealName() {
        return realName;
    }

    public String getSector() {
        return sector;
    }

    public String getMarket() {
        return market;
    }
}
