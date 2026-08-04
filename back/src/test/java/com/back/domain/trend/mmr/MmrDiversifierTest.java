package com.back.domain.trend.mmr;

import com.back.domain.trend.mmr.dto.MmrConfig;
import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MmrDiversifierTest {

    private final MmrDiversifier diversifier = new MmrDiversifier();

    // TrendAggregationEventHandler.pairKey()와 같은 정규화 규칙 —
    // 두 키워드를 결정적 순서로 정렬해 이어붙인다. 어느 쪽이 먼저인지는
    // 테스트와 프로덕션 구현이 같은 규칙만 따르면 되므로 여기서 직접 재정의한다.
    private String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "::" + b : b + "::" + a;
    }

    @Test
    @DisplayName("이미 뽑힌 키워드와 유사도가 높은 후보는 원점수가 더 높아도 순위가 밀려난다")
    void similarCandidateGetsPenalizedBelowLessSimilarLowerScoreOne() {
        RankedKeywordDto rain = new RankedKeywordDto("장마", 50, 10.0);
        RankedKeywordDto typhoon = new RankedKeywordDto("태풍", 45, 9.0);
        RankedKeywordDto umbrella = new RankedKeywordDto("우산", 40, 8.0);
        List<RankedKeywordDto> candidates = List.of(rain, typhoon, umbrella);

        Map<String, Double> similarities = Map.of(
                pairKey("장마", "태풍"), 0.9,
                pairKey("장마", "우산"), 0.1,
                pairKey("태풍", "우산"), 0.1
        );
        MmrConfig config = new MmrConfig(2.0, 1.0); // threshold=1.0이라 이 테스트에선 제외는 발생하지 않음

        List<RankedKeywordDto> result = diversifier.diversify(candidates, similarities, config, 3);

        // 원점수 순서는 장마(10) > 태풍(9) > 우산(8)이지만,
        // 태풍은 이미 뽑힌 장마와 유사도가 높아(0.9) 9 - 2*0.9 = 7.2로 깎이고
        // 우산은 장마와 유사도가 낮아(0.1) 8 - 2*0.1 = 7.8로 덜 깎여서 태풍보다 먼저 뽑힌다.
        assertThat(result).extracting(RankedKeywordDto::keyword)
                .containsExactly("장마", "우산", "태풍");
    }

    @Test
    @DisplayName("유사도가 임계값 이상이면 감점이 아니라 후보에서 완전히 제외된다")
    void candidateAboveThresholdIsExcludedEntirely() {
        RankedKeywordDto rain = new RankedKeywordDto("장마", 50, 10.0);
        RankedKeywordDto typhoon = new RankedKeywordDto("태풍", 45, 9.0);
        List<RankedKeywordDto> candidates = List.of(rain, typhoon);

        Map<String, Double> similarities = Map.of(pairKey("장마", "태풍"), 0.95);
        MmrConfig config = new MmrConfig(2.0, 0.9);

        List<RankedKeywordDto> result = diversifier.diversify(candidates, similarities, config, 2);

        assertThat(result).extracting(RankedKeywordDto::keyword).containsExactly("장마");
    }

    @Test
    @DisplayName("topN을 넘는 후보가 있으면 상위 N개까지만 반환된다")
    void limitedToTopN() {
        List<RankedKeywordDto> candidates = List.of(
                new RankedKeywordDto("A", 10, 4.0),
                new RankedKeywordDto("B", 10, 3.0),
                new RankedKeywordDto("C", 10, 2.0),
                new RankedKeywordDto("D", 10, 1.0)
        );

        List<RankedKeywordDto> result = diversifier.diversify(candidates, Map.of(), new MmrConfig(2.0, 0.9), 2);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RankedKeywordDto::keyword).containsExactly("A", "B");
    }

    @Test
    @DisplayName("유사도 정보가 없는 쌍은 무관(0)으로 취급되어 감점 없이 원래 점수 순서를 유지한다")
    void missingSimilarityDefaultsToZeroPenalty() {
        List<RankedKeywordDto> candidates = List.of(
                new RankedKeywordDto("A", 10, 5.0),
                new RankedKeywordDto("B", 10, 4.0),
                new RankedKeywordDto("C", 10, 3.0)
        );

        // similarities를 완전히 비워둔다 — 어떤 쌍에 대한 정보도 없음
        List<RankedKeywordDto> result = diversifier.diversify(candidates, Map.of(), new MmrConfig(2.0, 0.9), 3);

        assertThat(result).extracting(RankedKeywordDto::keyword).containsExactly("A", "B", "C");
    }
}
