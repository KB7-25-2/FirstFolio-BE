package org.firstfolio.simulation.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.simulation.domain.AssetType;
import org.firstfolio.simulation.domain.SimulationTerms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeCompressionPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 0, 0);

    private final TimeCompressionPolicy policy = new TimeCompressionPolicy(24);

    @ParameterizedTest(name = "실제 {0}개월 -> 서비스 {1}시간")
    @DisplayName("1개월을 1일(24시간)로 압축한다")
    @CsvSource({
            "1, 24",
            "3, 72",
            "6, 144",
            "12, 288",
            "24, 576",
            "36, 864"
    })
    void compressesOneMonthIntoOneDay(int maturityMonths, int expectedHours) {
        SimulationTerms terms =
                policy.compress(AssetType.DEPOSIT_SAVINGS, maturityMonths, NOW);

        assertEquals(expectedHours, terms.getServiceMaturityHours());
    }

    @Test
    @DisplayName("만기일시지급이라 이자 지급 주기가 만기와 같다")
    void interestIntervalMatchesMaturity() {
        SimulationTerms terms = policy.compress(AssetType.DEPOSIT_SAVINGS, 6, NOW);

        assertEquals(144, terms.getServiceMaturityHours());
        assertEquals(144, terms.getServiceInterestIntervalHours());
    }

    @Test
    @DisplayName("계산 근거로 적용 배율과 계산 시각을 남긴다")
    void recordsCalculationBasis() {
        SimulationTerms terms = policy.compress(AssetType.DEPOSIT_SAVINGS, 6, NOW);

        assertEquals(24, terms.getCompressionHoursPerMonth());
        assertEquals(NOW, terms.getCompressedAt());
    }

    @Test
    @DisplayName("주식은 압축 대상이 아니라 거부한다")
    void rejectsStock() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.compress(AssetType.STOCK, 12, NOW)
        );
    }

    @Test
    @DisplayName("펀드(ETF)도 만기가 없어 압축 대상이 아니다")
    void rejectsFund() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.compress(AssetType.FUND, 12, NOW)
        );
    }

    @Test
    @DisplayName("압축 대상은 예·적금과 채권뿐이다")
    void onlyDepositSavingsAndBondAreCompressed() {
        assertTrue(AssetType.DEPOSIT_SAVINGS.isTimeCompressed());
        assertTrue(AssetType.BOND.isTimeCompressed());
        assertFalse(AssetType.STOCK.isTimeCompressed());
        assertFalse(AssetType.FUND.isTimeCompressed());
    }

    @Test
    @DisplayName("만기를 알 수 없으면 임의로 계산하지 않는다")
    void rejectsUnknownMaturity() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> policy.compress(AssetType.DEPOSIT_SAVINGS, null, NOW)
        );

        assertEquals(ErrorCode.INVALID_SOURCE_PRODUCT, exception.getErrorCode());
        assertThrows(
                ApiException.class,
                () -> policy.compress(AssetType.DEPOSIT_SAVINGS, 0, NOW)
        );
    }

    @Test
    @DisplayName("배율은 설정값으로 바꿀 수 있다")
    void ratioIsConfigurable() {
        TimeCompressionPolicy hourly = new TimeCompressionPolicy(1);

        assertEquals(6, hourly.compress(AssetType.DEPOSIT_SAVINGS, 6, NOW).getServiceMaturityHours());
    }
}
