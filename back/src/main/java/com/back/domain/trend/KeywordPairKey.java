package com.back.domain.trend;

// 두 키워드를 결정적인 순서로 정렬해 하나의 문자열 키로 합친다.
// 어느 쪽이 먼저 나오든("장마","우산" / "우산","장마") 같은 키가 되어야
// Redis ZSET 멤버, MMR 유사도 맵 조회가 서로 일관되게 맞물린다.
public final class KeywordPairKey {

    private KeywordPairKey() {
    }

    public static String of(String keywordA, String keywordB) {
        return keywordA.compareTo(keywordB) <= 0
                ? keywordA + "::" + keywordB
                : keywordB + "::" + keywordA;
    }
}
