package com.back.domain.trend.dedup;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Component
public class MinHashDeduplicator {

    // 문서(메시지)를 몇 글자씩 끊어 "지문" 재료(shingle)로 만들지.
    // 채팅 메시지는 짧고 한국어 형태소 경계가 애매해서, 단어 단위보다 글자 단위 n-gram이 안전하다.
    private static final int SHINGLE_SIZE = 3;

    // 지문을 몇 조각(=몇 개의 독립적인 해시 함수)으로 만들지.
    // 참고 자료 기준 k=5. 값을 늘리면 유사도 추정이 더 촘촘해지는 대신 계산량이 늘어난다.
    private static final int SIGNATURE_LENGTH = 5;

    // 이 이상 겹치면 "같은 메시지"로 간주한다. (참고 자료 기준 90%)
    private static final double DUPLICATE_THRESHOLD = 0.9;

    // 32비트 해시값 범위(0 ~ 2^32-1)보다 큰 소수. 유니버설 해싱 h(x) = (a*x + b) mod PRIME의 법(modulus)으로 쓴다.
    private static final long HASH_PRIME = 4294967311L;

    // 빈 shingle 집합(빈 문자열)의 시그니처를 채울 값. 실제 해시값은 이 값이 될 수 없어(항상 PRIME 미만),
    // 빈 문자열끼리만 서로 매칭되고 실제 텍스트와는 절대 매칭되지 않는다.
    private static final long EMPTY_SENTINEL = Long.MAX_VALUE;

    // SIGNATURE_LENGTH개의 서로 다른 해시 함수를 흉내 내기 위한 계수쌍.
    // 고정 seed로 한 번만 생성해 재사용해야 같은 입력에 대해 항상 같은 시그니처가 나온다(결정성).
    private final long[] coefficientA;
    private final long[] coefficientB;

    public MinHashDeduplicator() {
        Random random = new Random(42);
        this.coefficientA = new long[SIGNATURE_LENGTH];
        this.coefficientB = new long[SIGNATURE_LENGTH];
        for (int i = 0; i < SIGNATURE_LENGTH; i++) {
            coefficientA[i] = 1 + (Math.abs(random.nextLong()) % (HASH_PRIME - 1));
            coefficientB[i] = random.nextLong() % HASH_PRIME;
            if (coefficientB[i] < 0) {
                coefficientB[i] += HASH_PRIME;
            }
        }
    }

    public long[] computeSignature(String text) {
        Set<String> shingles = extractShingles(text);

        long[] signature = new long[SIGNATURE_LENGTH];
        if (shingles.isEmpty()) {
            java.util.Arrays.fill(signature, EMPTY_SENTINEL);
            return signature;
        }
        java.util.Arrays.fill(signature, Long.MAX_VALUE);

        for (String shingle : shingles) {
            long baseHash = shingle.hashCode() & 0xFFFFFFFFL;
            for (int i = 0; i < SIGNATURE_LENGTH; i++) {
                long hashValue = (coefficientA[i] * baseHash + coefficientB[i]) % HASH_PRIME;
                if (hashValue < signature[i]) {
                    signature[i] = hashValue;
                }
            }
        }
        return signature;
    }

    public double estimateSimilarity(long[] signatureA, long[] signatureB) {
        int matches = 0;
        for (int i = 0; i < SIGNATURE_LENGTH; i++) {
            if (signatureA[i] == signatureB[i]) {
                matches++;
            }
        }
        return (double) matches / SIGNATURE_LENGTH;
    }

    public boolean isDuplicate(long[] signatureA, long[] signatureB) {
        return estimateSimilarity(signatureA, signatureB) >= DUPLICATE_THRESHOLD;
    }

    private Set<String> extractShingles(String text) {
        Set<String> shingles = new HashSet<>();
        if (text == null) {
            return shingles;
        }
        for (int i = 0; i + SHINGLE_SIZE <= text.length(); i++) {
            shingles.add(text.substring(i, i + SHINGLE_SIZE));
        }
        return shingles;
    }
}
