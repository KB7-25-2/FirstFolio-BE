package org.firstfolio.simulation.service;

import org.firstfolio.simulation.domain.AssetType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 정규장 시간과 자산군별 거래 가능 시간 (FUNC-035, v3 3.1절).
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
 * <h3>두 가지 질문을 나눠서 답한다</h3>
 *
 * <ul>
 *   <li>{@link #isMarketOpen(LocalDateTime)} — <b>지금 장이 열려 있나.</b> 자산군과 무관한
 *       시장 자체의 상태다. 시세 폴링처럼 "장중에만 할 일"이 쓴다.</li>
 *   <li>{@link #isOpen(AssetType, LocalDateTime)} — <b>지금 이 자산군을 거래할 수 있나.</b>
 *       가입형은 장과 무관하게 24시간이므로 위 판정을 타지 않는다.</li>
 * </ul>
 *
 * <p>거래 검증만 있을 때는 후자 하나였는데, 가격 스케줄러가 자산군 없이 시장 상태만 물으면서
 * 앞의 것을 분리했다. 자산군 없이 물으려고 {@code AssetType.STOCK}을 넘기는 코드가 생기면
 * "왜 하필 주식인가"를 읽는 사람이 매번 되짚어야 한다.</p>
 *
 * <h3>서버는 UTC로 돈다</h3>
 *
 * <p>정규장은 한국 시간 기준이므로 {@code Asia/Seoul}로 바꿔서 판정한다. 서버 JVM 시간대에
 * 기대면 배포 환경에 따라 거래 가능 시간이 달라진다.</p>
 *
 * <p><b>공휴일은 판정하지 않는다.</b> v3에 휴장일 규칙이 없다 — 공휴일에는 시세가 갱신되지 않아
 * 마지막 체결가로 거래된다. 휴장일 API 연동은 2차 후보다.</p>
 *
 * <h3>이 클래스가 {@code simulation}에 있는 이유</h3>
 *
 * <p>정규장 시간은 <b>시장의 속성</b>이지 포트폴리오의 속성이 아니다. 거래 검증(portfolio)과
 * 시세 폴링(simulation) 양쪽이 쓰는데, 이 레포의 패키지 의존은 {@code portfolio → simulation}
 * 한 방향이다. {@code portfolio}에 두면 시세 폴링이 반대 방향 의존을 만든다.</p>
 */
@Component
public class TradingHours {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    /** 정규장. v3 3.1절 확정값. */
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    /**
     * 지금 정규장이 열려 있는지. <b>자산군과 무관한 시장 자체의 상태다.</b>
     *
     * <p>평일 09:00~15:30 KST. 공휴일은 판정하지 않는다.</p>
     *
     * @param nowUtc 서버 시각(UTC)
     */
    public boolean isMarketOpen(LocalDateTime nowUtc) {
        ZonedDateTime korea = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA);

        if (isWeekend(korea.getDayOfWeek())) {
            return false;
        }

        LocalTime time = korea.toLocalTime();

        // 15:30 정각은 아직 장중으로 본다 (마감 체결 포함).
        return !time.isBefore(OPEN) && !time.isAfter(CLOSE);
    }

    /**
     * 오늘 정규장이 이미 끝났는지. <b>평일이면서 마감 시각을 지났을 때만 참이다.</b>
     *
     * <p>"장이 안 열려 있다"와는 다르다 — 개장 전(평일 오전)도 닫혀 있지만 <b>아직 끝난 것은
     * 아니다.</b> 종가는 그날 장이 끝난 뒤에만 확정되므로 둘을 구분해야 한다. 구분하지 않으면
     * 금요일 종가를 토요일·월요일 아침에 그날 종가로 다시 저장하게 된다.</p>
     *
     * <p>15:30 정각은 아직 장중이므로({@link #isMarketOpen}) 여기서는 거짓이다.</p>
     *
     * @param nowUtc 서버 시각(UTC)
     */
    public boolean isAfterClose(LocalDateTime nowUtc) {
        ZonedDateTime korea = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA);

        if (isWeekend(korea.getDayOfWeek())) {
            return false;
        }

        return korea.toLocalTime().isAfter(CLOSE);
    }

    /**
     * 한국 기준 날짜. "하루에 한 번"을 세는 기준이다.
     *
     * <p>서버는 UTC로 도는데 UTC 날짜로 세면 KST 09:00 이전이 전날로 잡혀 하루가 어긋난다.</p>
     */
    public LocalDate koreaDate(LocalDateTime nowUtc) {
        return nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toLocalDate();
    }

    /**
     * 그 거래일의 마감 시각을 서버 시각(UTC)으로. <b>"오늘 마감 후"의 경계다.</b>
     *
     * <p>시간대 변환을 SQL에 넘기지 않으려고 자바에서 계산한다 — 질의는 이 값과 비교만 한다.</p>
     */
    public LocalDateTime closeAtUtc(LocalDate koreaDate) {
        return ZonedDateTime.of(koreaDate, CLOSE, KOREA)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

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

        return isMarketOpen(nowUtc);
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
