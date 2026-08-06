package com.back.domain.trend.mmr;

import com.back.domain.trend.KeywordPairKey;
import com.back.domain.trend.mmr.dto.MmrConfig;
import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MmrDiversifierTest {

    private final MmrDiversifier diversifier = new MmrDiversifier();

    @Test
    @DisplayName("이미 뽑힌 키워드와 유사도가 높은 후보는 원점수가 더 높아도 순위가 밀려난다")
    void similarCandidateGetsPenalizedBelowLessSimilarLowerScoreOne() {
        RankedKeywordDto rain = new RankedKeywordDto("장마", 50, 10.0);
        RankedKeywordDto typhoon = new RankedKeywordDto("태풍", 45, 9.0);
        RankedKeywordDto umbrella = new RankedKeywordDto("우산", 40, 8.0);
        List<RankedKeywordDto> candidates = List.of(rain, typhoon, umbrella);

        Map<String, Double> similarities = Map.of(
                KeywordPairKey.of("장마", "태풍"), 0.9,
                KeywordPairKey.of("장마", "우산"), 0.1,
                KeywordPairKey.of("태풍", "우산"), 0.1
        );
        MmrConfig config = new MmrConfig(2.0, 1.0); // threshold=1.0이라 이 테스트에선 제외는 발생하지 않음

        List<RankedKeywordDto> result = diversifier.diversify(candidates, similarities, config, 3);

        // 원점수 순서는 장마(10) > 태풍(9) > 우산(8)이지만,
        // 태풍은 이미 뽑힌 장마와 유사도가 높아(0.9) 9 - 2*0.9 = 7.2로 깎이고
        // 우산은 장마와 유사도가 낮아(0.1) 8 - 2*0.1 = 7.8로 덜 깎여서 태풍보다 먼저 뽑힌다.
        assertThat(result).extracting(RankedKeywordDto::getKeyword)
                .containsExactly("장마", "우산", "태풍");
    }

    @Test
    @DisplayName("유사도가 임계값 이상이면 감점이 아니라 후보에서 완전히 제외된다")
    void candidateAboveThresholdIsExcludedEntirely() {
        RankedKeywordDto rain = new RankedKeywordDto("장마", 50, 10.0);
        RankedKeywordDto typhoon = new RankedKeywordDto("태풍", 45, 9.0);
        List<RankedKeywordDto> candidates = List.of(rain, typhoon);

        Map<String, Double> similarities = Map.of(KeywordPairKey.of("장마", "태풍"), 0.95);
        MmrConfig config = new MmrConfig(2.0, 0.9);

        List<RankedKeywordDto> result = diversifier.diversify(candidates, similarities, config, 2);

        assertThat(result).extracting(RankedKeywordDto::getKeyword).containsExactly("장마");
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
        assertThat(result).extracting(RankedKeywordDto::getKeyword).containsExactly("A", "B");
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

        assertThat(result).extracting(RankedKeywordDto::getKeyword).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("후보 목록이 비어 있으면 빈 리스트를 반환한다")
    void emptyCandidatesReturnsEmptyList() {
        List<RankedKeywordDto> result = diversifier.diversify(List.of(), Map.of(), new MmrConfig(2.0, 0.9), 3);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("topN이 0 이하이면 빈 리스트를 반환한다")
    void nonPositiveTopNReturnsEmptyList() {
        List<RankedKeywordDto> candidates = List.of(new RankedKeywordDto("A", 10, 5.0));

        List<RankedKeywordDto> result = diversifier.diversify(candidates, Map.of(), new MmrConfig(2.0, 0.9), 0);

        assertThat(result).isEmpty();
    }
}
