package com.back.domain.trend.keyword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NounExtractorTest {

    private final NounExtractor nounExtractor = new NounExtractor();

    @Test
    @DisplayName("웃음 표현(ㅋㅋㅋ)과 조사/어미는 제외하고 명사만 남긴다")
    void extractsOnlyNouns() {
        List<String> result = nounExtractor.extract("오늘 장마 시작이래ㅋㅋㅋ 다들 우산 챙기세요");

        // "오늘"은 세종 태그셋에서 명사가 아니라 부사(MAG)로 분석되어 자연스럽게 빠진다.
        assertThat(result).contains("장마", "시작", "우산");
        assertThat(result).doesNotContain("이래", "다들", "챙기세요");
    }

    @Test
    @DisplayName("감사 인사에서도 명사 어근(감사, 최고)은 추출된다 - 반복 억제는 Z-테스트 몫")
    void greetingNounRootsAreStillExtracted() {
        List<String> result = nounExtractor.extract("감사합니다!! 😊 진짜 최고예요");

        // "감사"(NNG), "최고"(NNG)는 문법적으로 진짜 명사라 품사 필터만으로는 못 걸러낸다.
        // 이런 매일 반복되는 인사성 명사는 Z-테스트(일주일 전 비교)에서 증가폭이 없어 걸러지는 설계다.
        assertThat(result).contains("감사", "최고");
        assertThat(result).doesNotContain("진짜", "합니다", "예요");
    }

    @Test
    @DisplayName("사전에 등록된 복합명사(대한민국)는 유지되지만, 미등록 조합(대표팀)은 분리된다")
    void compoundSplittingDependsOnDictionaryEntry() {
        List<String> result = nounExtractor.extract("대한민국 대표팀 경기 대박이었다");

        // DecompoundMode.NONE은 "사전에 등록된 복합명사"를 안 쪼갠다는 뜻이지, 모든 명사 조합을 붙여준다는 뜻이 아니다.
        // "대한민국"은 mecab-ko-dic에 단일 표제어로 등록돼 있어 통째로 남지만,
        // "대표팀"은 그런 등록이 없어 "대표"+"팀"으로 자연 분리된다.
        assertThat(result).contains("대한민국", "대표", "팀", "경기");
    }

    @Test
    @DisplayName("빈 문자열은 빈 리스트를 반환한다")
    void emptyTextReturnsEmptyList() {
        assertThat(nounExtractor.extract("")).isEmpty();
    }

    @Test
    @DisplayName("null 입력은 예외 없이 빈 리스트를 반환한다")
    void nullTextReturnsEmptyList() {
        assertThat(nounExtractor.extract(null)).isEmpty();
    }
}
