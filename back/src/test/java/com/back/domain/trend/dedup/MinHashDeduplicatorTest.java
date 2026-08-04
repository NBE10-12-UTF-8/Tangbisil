package com.back.domain.trend.dedup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinHashDeduplicatorTest {

    private final MinHashDeduplicator minHashDeduplicator = new MinHashDeduplicator();

    @Test
    @DisplayName("같은 문장은 항상 같은 시그니처를 낸다 (결정적이어야 함)")
    void sameSentenceProducesSameSignature() {
        long[] signature1 = minHashDeduplicator.computeSignature("오늘 점심 뭐 먹지");
        long[] signature2 = minHashDeduplicator.computeSignature("오늘 점심 뭐 먹지");

        assertThat(signature1).isEqualTo(signature2);
    }

    @Test
    @DisplayName("완전히 같은 문장의 유사도는 1.0이다")
    void identicalTextsHaveSimilarityOne() {
        long[] signature1 = minHashDeduplicator.computeSignature("점심 뭐 먹지");
        long[] signature2 = minHashDeduplicator.computeSignature("점심 뭐 먹지");

        assertThat(minHashDeduplicator.estimateSimilarity(signature1, signature2)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("공백 하나만 다른 거의 동일한 문장은, 전혀 무관한 문장보다 유사도가 확실히 높다")
    void nearDuplicateIsMoreSimilarThanUnrelatedText() {
        long[] original = minHashDeduplicator.computeSignature("오늘 점심 뭐 먹지");
        long[] nearDuplicate = minHashDeduplicator.computeSignature("오늘 점심 뭐먹지");
        long[] unrelated = minHashDeduplicator.computeSignature("주식이 왜 이렇게 빠지지");

        double nearDuplicateSimilarity = minHashDeduplicator.estimateSimilarity(original, nearDuplicate);
        double unrelatedSimilarity = minHashDeduplicator.estimateSimilarity(original, unrelated);

        assertThat(nearDuplicateSimilarity).isGreaterThan(unrelatedSimilarity);
    }

    @Test
    @DisplayName("완전히 같은 문장은 중복으로 판단한다")
    void identicalTextsAreDuplicates() {
        long[] signature1 = minHashDeduplicator.computeSignature("완전히 똑같은 메시지입니다");
        long[] signature2 = minHashDeduplicator.computeSignature("완전히 똑같은 메시지입니다");

        assertThat(minHashDeduplicator.isDuplicate(signature1, signature2)).isTrue();
    }

    @Test
    @DisplayName("전혀 무관한 문장은 중복이 아니다")
    void unrelatedTextsAreNotDuplicates() {
        long[] signature1 = minHashDeduplicator.computeSignature("오늘 점심 뭐 먹지");
        long[] signature2 = minHashDeduplicator.computeSignature("주식이 왜 이렇게 빠지지");

        assertThat(minHashDeduplicator.isDuplicate(signature1, signature2)).isFalse();
    }

    @Test
    @DisplayName("빈 문자열끼리도 시그니처는 결정적이지만, 일반 메시지와는 중복으로 판단되지 않는다")
    void blankTextNeverMatchesNormalTextAsDuplicate() {
        long[] blankSignature = minHashDeduplicator.computeSignature("");
        long[] normalSignature = minHashDeduplicator.computeSignature("아무 말이나 해본다");

        assertThat(minHashDeduplicator.isDuplicate(blankSignature, normalSignature)).isFalse();
    }
}
