package org.firstfolio.portfolio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioEventBatchRunnerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 5, 0);

    private PortfolioEventService eventService;

    @BeforeEach
    void setUp() {
        eventService = mock(PortfolioEventService.class);

        when(eventService.process(any(), any()))
                .thenReturn(new PortfolioEventBatchResult(3, 3, 0, 0));
    }

    private PortfolioEventBatchRunner runner(boolean enabled) {
        Clock fixed = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        return new PortfolioEventBatchRunner(eventService, fixed, enabled);
    }

    @Test
    @DisplayName("켜져 있으면 도래한 이벤트를 처리한다")
    void processesDueEventsWhenEnabled() {
        runner(true).processDueEvents();

        verify(eventService).process(NOW, null);
    }

    @Test
    @DisplayName("기본은 꺼져 있다 — 이자 계산식이 아직 승인되지 않은 가정치다")
    void doesNothingWhenDisabled() {
        runner(false).processDueEvents();

        verify(eventService, never()).process(any(), any());
    }

    @Test
    @DisplayName("기준 시각은 지금이다 — 미래 시각은 서비스가 거부한다")
    void usesNowAsProcessUntil() {
        ArgumentCaptor<LocalDateTime> processUntil = ArgumentCaptor.forClass(LocalDateTime.class);

        runner(true).processDueEvents();

        verify(eventService).process(processUntil.capture(), isNull());
        assertEquals(NOW, processUntil.getValue());
    }

    @Test
    @DisplayName("배치 크기를 지정하지 않는다 — 서비스 기본값을 쓴다")
    void leavesBatchSizeToService() {
        runner(true).processDueEvents();

        verify(eventService).process(any(), isNull());
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 내보내지 않는다 — 반복 실행이 멈추면 안 된다")
    void swallowsFailureSoScheduleKeepsRunning() {
        when(eventService.process(any(), any()))
                .thenThrow(new IllegalStateException("DB 연결 실패"));

        runner(true).processDueEvents();
    }

    @Test
    @DisplayName("한 번 실패해도 다음 주기에 다시 시도한다")
    void retriesOnNextTick() {
        when(eventService.process(any(), any()))
                .thenThrow(new IllegalStateException("일시 장애"))
                .thenReturn(new PortfolioEventBatchResult(1, 1, 0, 0));

        PortfolioEventBatchRunner runner = runner(true);

        runner.processDueEvents();
        runner.processDueEvents();

        verify(eventService, times(2)).process(any(), any());
    }

    @Test
    @DisplayName("처리할 이벤트가 없어도 정상이다")
    void toleratesEmptyBatch() {
        when(eventService.process(any(), any()))
                .thenReturn(new PortfolioEventBatchResult(0, 0, 0, 0));

        runner(true).processDueEvents();

        verify(eventService).process(NOW, null);
    }

    // ------------------------------------------------------------- 배포되는 기본값

    /**
     * 위 테스트들은 {@code enabled}를 생성자로 직접 넘기므로 <b>실제 기본값을 지나지 않는다.</b>
     * 켜진 채로 배포되면 승인되지 않은 계산식으로 돈이 지급되고, 지급된 이력은 되돌리지 않는다.
     * 그래서 배포되는 설정 자체를 확인한다.
     */
    @Test
    @DisplayName("환경변수가 없으면 꺼진 채로 배포된다 — 승인 전 자동 지급을 막는 마지막 장치다")
    void shipsDisabledByDefault() throws IOException {
        Properties properties = new Properties();

        try (InputStream stream = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(stream, "application.properties를 찾지 못했습니다.");
            properties.load(stream);
        }

        String configured = properties.getProperty("portfolio.event.batch.enabled");

        assertNotNull(configured, "설정 키가 사라졌습니다.");
        assertTrue(
                configured.endsWith(":false}"),
                "환경변수 미설정 시 꺼져 있어야 합니다. 현재: " + configured
        );
    }
}
