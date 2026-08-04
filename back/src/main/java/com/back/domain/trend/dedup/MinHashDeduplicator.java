package com.back.domain.trend.dedup;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Component
public class MinHashDeduplicator {

    private static final int SHINGLE_SIZE = 3;
    private static final int SIGNATURE_LENGTH = 20;
    private static final double DUPLICATE_THRESHOLD = 0.9;
    private static final long HASH_PRIME = 2147483647L;
    private static final long EMPTY_SENTINEL = Long.MAX_VALUE;

    private final long[] coefficientA;
    private final long[] coefficientB;

    public MinHashDeduplicator() {
        Random random = new Random(42);
        this.coefficientA = new long[SIGNATURE_LENGTH];
        this.coefficientB = new long[SIGNATURE_LENGTH];
        for (int i = 0; i < SIGNATURE_LENGTH; i++) {
            coefficientA[i] = 1 + ((random.nextLong() & Long.MAX_VALUE) % (HASH_PRIME - 1));
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
            Arrays.fill(signature, EMPTY_SENTINEL);
            return signature;
        }
        Arrays.fill(signature, Long.MAX_VALUE);

        for (String shingle : shingles) {
            long baseHash = (shingle.hashCode() & 0xFFFFFFFFL) % HASH_PRIME;
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

    public boolean canFingerprint(String text) {
        return text != null && text.length() >= SHINGLE_SIZE;
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
