package org.firstfolio.portfolio.service;

import org.firstfolio.simulation.domain.AssetType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 거래 가능 시간 (FUNC-035, v3 3.1절).
 *
 * <table>
 *   <tr><th>자산군</th><th>거래 가능 시간</th></tr>
 *   <tr><td>주식·펀드</td><td><b>09:00~15:30 KST, 주말 제외</b></td></tr>
 *   <tr><td>예·적금·채권</td><td>24시간</td></tr>
 * </table>
 *
 * <p><b>v3는 펀드를 24시간으로 두지만 팀이 주식과 동일하게 확정했다</b> (2026-08-07).
 * 실제 ETF가 정규장에만 거래되므로 현실에 더 맞다. 정책 문서 수정 요청 대상이다.</p>
 *
 * <h3>서버는 UTC로 돈다</h3>
 *
 * <p>정규장은 한국 시간 기준이므로 {@code Asia/Seoul}로 바꿔서 판정한다. 서버 JVM 시간대에
 * 기대면 배포 환경에 따라 거래 가능 시간이 달라진다.</p>
 *
 * <p><b>공휴일은 판정하지 않는다.</b> v3에 휴장일 규칙이 없다 — 공휴일에는 시세가 갱신되지 않아
 * 마지막 체결가로 거래된다. 휴장일 API 연동은 2차 후보다.</p>
 */
@Component
public class TradingHours {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    /** 정규장. v3 3.1절 확정값. */
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    /**
     * 지금 이 자산군을 거래할 수 있는지.
     *
     * @param nowUtc 서버 시각(UTC)
     */
    public boolean isOpen(AssetType assetType, LocalDateTime nowUtc) {
        if (assetType == null || assetType.isTimeCompressed()) {
            // 예·적금·채권은 24시간. 시세가 아니라 원금으로 평가되므로 장이 열릴 필요가 없다.
            return true;
        }

        ZonedDateTime korea = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA);

        if (isWeekend(korea.getDayOfWeek())) {
            return false;
        }

        LocalTime time = korea.toLocalTime();

        // 15:30 정각은 아직 장중으로 본다 (마감 체결 포함).
        return !time.isBefore(OPEN) && !time.isAfter(CLOSE);
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
