package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingHoursTest {

    private final TradingHours tradingHours = new TradingHours();

    /** 한국 시각을 서버가 쓰는 UTC로 바꾼다. 테스트를 KST로 읽을 수 있게 하기 위함이다. */
    private static LocalDateTime utcOf(String koreaDateTime) {
        return ZonedDateTime.of(LocalDateTime.parse(koreaDateTime), ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    // ------------------------------------------------------------- 자산군별 거래 가능 시간

    @ParameterizedTest(name = "KST {0} → 거래 {1}")
    @DisplayName("주식은 평일 09:00~15:30에만 거래할 수 있다")
    @CsvSource({
            "2026-08-06T08:59:59, false",
            "2026-08-06T09:00:00, true",
            "2026-08-06T12:00:00, true",
            "2026-08-06T15:30:00, true",
            "2026-08-06T15:30:01, false",
            "2026-08-06T23:59:59, false",
            "2026-08-06T03:00:00, false"
    })
    void allowsStockTradeOnlyDuringRegularSession(String koreaDateTime, boolean expected) {
        assertTrue(tradingHours.isOpen(AssetType.STOCK, utcOf(koreaDateTime)) == expected);
    }

    @Test
    @DisplayName("펀드도 주식과 같은 시간에만 거래한다 — v3의 24시간에서 바뀐 부분")
    void treatsFundLikeStock() {
        assertTrue(tradingHours.isOpen(AssetType.FUND, utcOf("2026-08-06T12:00:00")));
        assertFalse(tradingHours.isOpen(AssetType.FUND, utcOf("2026-08-06T16:00:00")));
    }

    @Test
    @DisplayName("주말에는 주식·펀드를 거래할 수 없다")
    void closesOnWeekend() {
        // 2026-08-08은 토요일, 08-09는 일요일 — 장중 시간이어도 닫혀 있다.
        assertFalse(tradingHours.isOpen(AssetType.STOCK, utcOf("2026-08-08T12:00:00")));
        assertFalse(tradingHours.isOpen(AssetType.FUND, utcOf("2026-08-09T12:00:00")));
    }

    @ParameterizedTest
    @DisplayName("예·적금·채권은 24시간 거래할 수 있다")
    @EnumSource(value = AssetType.class, names = {"DEPOSIT_SAVINGS", "BOND"})
    void allowsSubscriptionAnyTime(AssetType assetType) {
        assertTrue(tradingHours.isOpen(assetType, utcOf("2026-08-06T03:00:00")));
        assertTrue(tradingHours.isOpen(assetType, utcOf("2026-08-09T23:00:00")), "주말도 가능합니다.");
    }

    @Test
    @DisplayName("서버 시각을 UTC로 받아 한국 시간으로 판정한다")
    void judgesInKoreaTimeFromUtcInput() {
        // UTC 00:30 = KST 09:30 (장중) / UTC 09:00 = KST 18:00 (마감 후)
        assertTrue(tradingHours.isOpen(AssetType.STOCK, LocalDateTime.parse("2026-08-06T00:30:00")));
        assertFalse(tradingHours.isOpen(AssetType.STOCK, LocalDateTime.parse("2026-08-06T09:00:00")));
    }

    @Test
    @DisplayName("자산군을 모르면 막지 않는다 — 시간 검증이 거래를 잘못 거부하지 않게 한다")
    void doesNotBlockWhenAssetTypeIsUnknown() {
        assertTrue(tradingHours.isOpen(null, utcOf("2026-08-09T23:00:00")));
    }

    // ------------------------------------------------------------- 시장 자체의 개장 여부

    @ParameterizedTest(name = "KST {0} → 개장 {1}")
    @DisplayName("정규장은 평일 09:00~15:30이다 — 경계 포함 여부까지")
    @CsvSource({
            "2026-08-06T08:59:59, false",
            "2026-08-06T09:00:00, true",
            "2026-08-06T15:30:00, true",
            "2026-08-06T15:30:01, false",
            "2026-08-06T18:00:00, false"
    })
    void marketOpensOnlyDuringRegularSession(String koreaDateTime, boolean expected) {
        assertTrue(tradingHours.isMarketOpen(utcOf(koreaDateTime)) == expected);
    }

    @Test
    @DisplayName("주말에는 장이 열리지 않는다")
    void marketClosesOnWeekend() {
        assertFalse(tradingHours.isMarketOpen(utcOf("2026-08-08T12:00:00")), "토요일");
        assertFalse(tradingHours.isMarketOpen(utcOf("2026-08-09T12:00:00")), "일요일");
    }

    @Test
    @DisplayName("개장 판정은 자산군을 묻지 않는다 — 가입형이어도 장은 닫혀 있다")
    void marketStateIsIndependentOfAssetType() {
        LocalDateTime afterClose = utcOf("2026-08-06T18:00:00");

        // 예·적금은 24시간 '거래'할 수 있지만, 그것과 '장이 열렸는지'는 다른 질문이다.
        assertTrue(tradingHours.isOpen(AssetType.DEPOSIT_SAVINGS, afterClose));
        assertFalse(tradingHours.isMarketOpen(afterClose));
    }

    // ------------------------------------------------------------- 오늘 장이 끝났는가

    @ParameterizedTest(name = "KST {0} → 마감 지남 {1}")
    @DisplayName("마감 판정은 평일 15:30을 지난 뒤에만 참이다")
    @CsvSource({
            "2026-08-06T08:00:00, false",
            "2026-08-06T12:00:00, false",
            "2026-08-06T15:30:00, false",
            "2026-08-06T15:30:01, true",
            "2026-08-06T23:59:59, true"
    })
    void marksAfterCloseOnlyPastClosingTime(String koreaDateTime, boolean expected) {
        assertTrue(tradingHours.isAfterClose(utcOf(koreaDateTime)) == expected);
    }

    @Test
    @DisplayName("개장 전은 '닫혀 있다'이지만 '끝났다'는 아니다 — 둘을 섞으면 전날 종가를 다시 저장한다")
    void separatesBeforeOpenFromAfterClose() {
        LocalDateTime beforeOpen = utcOf("2026-08-06T08:00:00");

        assertFalse(tradingHours.isMarketOpen(beforeOpen), "아직 안 열렸습니다.");
        assertFalse(tradingHours.isAfterClose(beforeOpen), "하지만 오늘 장이 끝난 것도 아닙니다.");
    }

    @Test
    @DisplayName("주말은 마감 판정에서도 거짓이다 — 토요일에 금요일 종가를 또 저장하면 안 된다")
    void neverMarksAfterCloseOnWeekend() {
        assertFalse(tradingHours.isAfterClose(utcOf("2026-08-08T18:00:00")), "토요일");
        assertFalse(tradingHours.isAfterClose(utcOf("2026-08-09T18:00:00")), "일요일");
    }

    @Test
    @DisplayName("개장·마감 판정이 동시에 참일 수 없다")
    void openAndAfterCloseAreExclusive() {
        for (String koreaDateTime : new String[]{
                "2026-08-06T08:59:59",
                "2026-08-06T09:00:00",
                "2026-08-06T15:30:00",
                "2026-08-06T15:30:01",
                "2026-08-08T12:00:00"
        }) {
            LocalDateTime at = utcOf(koreaDateTime);

            assertFalse(
                    tradingHours.isMarketOpen(at) && tradingHours.isAfterClose(at),
                    koreaDateTime + " — 장중이면서 마감 후일 수는 없습니다."
            );
        }
    }

    // ------------------------------------------------------------- 거래일 판정

    @Test
    @DisplayName("거래일은 한국 날짜다 — UTC로 세면 오전이 전날로 잡힌다")
    void countsDayInKoreaTime() {
        // KST 2026-08-06 08:00 = UTC 2026-08-05 23:00
        assertEquals(LocalDate.of(2026, 8, 6), tradingHours.koreaDate(utcOf("2026-08-06T08:00:00")));
        assertEquals(LocalDate.of(2026, 8, 6), tradingHours.koreaDate(utcOf("2026-08-06T23:59:59")));
    }

    @Test
    @DisplayName("자정을 넘기면 거래일도 넘어간다")
    void rollsOverAtKoreanMidnight() {
        assertEquals(LocalDate.of(2026, 8, 6), tradingHours.koreaDate(utcOf("2026-08-06T23:59:59")));
        assertEquals(LocalDate.of(2026, 8, 7), tradingHours.koreaDate(utcOf("2026-08-07T00:00:00")));
    }

    @Test
    @DisplayName("주식·펀드의 거래 가능 시간은 정규장과 정확히 같다")
    void marketPricedAssetsFollowMarketHours() {
        // 두 판정이 갈라지면 폴링은 도는데 거래는 막히는(혹은 반대) 상태가 생긴다.
        for (String koreaDateTime : new String[]{
                "2026-08-06T08:59:59",
                "2026-08-06T09:00:00",
                "2026-08-06T15:30:00",
                "2026-08-06T15:30:01",
                "2026-08-08T12:00:00"
        }) {
            LocalDateTime at = utcOf(koreaDateTime);

            assertTrue(
                    tradingHours.isMarketOpen(at) == tradingHours.isOpen(AssetType.STOCK, at),
                    koreaDateTime + " — 정규장과 주식 거래 가능 시간이 어긋납니다."
            );
            assertTrue(
                    tradingHours.isMarketOpen(at) == tradingHours.isOpen(AssetType.FUND, at),
                    koreaDateTime + " — 정규장과 펀드 거래 가능 시간이 어긋납니다."
            );
        }
    }
}
