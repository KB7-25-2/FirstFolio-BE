package org.firstfolio.portfolio.service;

import org.firstfolio.simulation.domain.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
}
