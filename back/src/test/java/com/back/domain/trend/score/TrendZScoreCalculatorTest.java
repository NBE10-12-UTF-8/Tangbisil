package com.back.domain.trend.score;

import com.back.domain.trend.score.dto.WordFrequencyStatsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TrendZScoreCalculatorTest {

    private final TrendZScoreCalculator calculator = new TrendZScoreCalculator();

    @Test
    @DisplayName("빈도가 뚜렷하게 늘어난 단어는 높은 양의 Z값을 갖는다")
    void clearIncreaseProducesHighPositiveZ() {
        // 기준일: 1000건 중 10건(1%) -> 오늘: 1000건 중 50건(5%)
        WordFrequencyStatsDto baseline = new WordFrequencyStatsDto(10, 1000);
        WordFrequencyStatsDto current = new WordFrequencyStatsDto(50, 1000);

        double z = calculator.calculate(baseline, current);

        assertThat(z).isCloseTo(5.244, offset(0.01));
    }

    @Test
    @DisplayName("빈도가 줄어든 단어는 음의 Z값을 갖는다")
    void decreaseProducesNegativeZ() {
        // 기준일: 10% -> 오늘: 5%
        WordFrequencyStatsDto baseline = new WordFrequencyStatsDto(100, 1000);
        WordFrequencyStatsDto current = new WordFrequencyStatsDto(50, 1000);

        double z = calculator.calculate(baseline, current);

        assertThat(z).isNegative();
    }

    @Test
    @DisplayName("기준일에 전혀 없던 신규 단어도 정상적으로 Z값이 계산된다")
    void brandNewWordStillComputesPositiveZ() {
        // 기준일: 0% -> 오늘: 1000건 중 20건(2%)
        WordFrequencyStatsDto baseline = new WordFrequencyStatsDto(0, 1000);
        WordFrequencyStatsDto current = new WordFrequencyStatsDto(20, 1000);

        double z = calculator.calculate(baseline, current);

        assertThat(z).isCloseTo(4.496, offset(0.01));
    }

    @Test
    @DisplayName("두 기간 모두 등장하지 않은 단어는 NaN이 아니라 0을 반환한다")
    void noOccurrenceAnywhereReturnsZeroNotNaN() {
        WordFrequencyStatsDto baseline = new WordFrequencyStatsDto(0, 1000);
        WordFrequencyStatsDto current = new WordFrequencyStatsDto(0, 1000);

        double z = calculator.calculate(baseline, current);

        assertThat(z).isZero();
        assertThat(Double.isNaN(z)).isFalse();
    }
}
