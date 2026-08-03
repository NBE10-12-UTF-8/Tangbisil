package com.back.domain.trend.npmi;

import com.back.domain.trend.npmi.dto.CooccurrenceStatsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class NpmiCalculatorTest {

    private final NpmiCalculator calculator = new NpmiCalculator();

    @Test
    @DisplayName("두 단어가 각자 등장하는 것보다 훨씬 자주 같이 등장하면 NPMI가 1에 가까운 양수가 된다")
    void stronglyCorrelatedWordsProduceHighPositiveNpmi() {
        // 전체 1000건 중 x 100건, y 100건, 그중 80건은 같은 메시지에 동시 등장
        CooccurrenceStatsDto stats = new CooccurrenceStatsDto(100, 100, 80, 1000);

        double npmi = calculator.calculate(stats);

        assertThat(npmi).isCloseTo(0.8233, offset(0.001));
    }

    @Test
    @DisplayName("두 단어의 동시 등장 확률이 각자 등장 확률의 곱과 같으면(=서로 무관하면) NPMI는 0이다")
    void independentWordsProduceZeroNpmi() {
        // p(x)=0.1, p(y)=0.1 이면 무관할 때 기대되는 p(x,y)는 0.1*0.1=0.01 -> 1000건 중 10건
        CooccurrenceStatsDto stats = new CooccurrenceStatsDto(100, 100, 10, 1000);

        double npmi = calculator.calculate(stats);

        assertThat(npmi).isCloseTo(0.0, offset(0.0001));
    }

    @Test
    @DisplayName("두 단어가 각자는 등장했지만 같은 메시지에 단 한 번도 같이 등장하지 않았다면 NPMI는 정확히 -1이다")
    void neverCooccurringWordsProduceMinusOne() {
        CooccurrenceStatsDto stats = new CooccurrenceStatsDto(50, 50, 0, 1000);

        double npmi = calculator.calculate(stats);

        assertThat(npmi).isEqualTo(-1.0);
    }

    @Test
    @DisplayName("두 단어가 모든 메시지에 항상 같이 등장했다면(freqXY == totalMessages) NPMI는 정확히 1이다")
    void alwaysCooccurringWordsProduceOne() {
        CooccurrenceStatsDto stats = new CooccurrenceStatsDto(1000, 1000, 1000, 1000);

        double npmi = calculator.calculate(stats);

        assertThat(npmi).isEqualTo(1.0);
    }

    @Test
    @DisplayName("totalMessages가 0 이하이면 NaN/Infinity 대신 0을 반환한다")
    void nonPositiveTotalMessagesReturnsZeroNotNaN() {
        CooccurrenceStatsDto stats = new CooccurrenceStatsDto(10, 10, 5, 0);

        double npmi = calculator.calculate(stats);

        assertThat(npmi).isZero();
        assertThat(Double.isNaN(npmi)).isFalse();
        assertThat(Double.isInfinite(npmi)).isFalse();
    }
}
