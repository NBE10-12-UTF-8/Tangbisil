package com.back.global.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions; // JUnit 가정(Assumption) 클래스 추가
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import java.net.Socket; // 소켓 연결 확인용 임포트

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 1. 로컬 6379 포트에 레디스 서버가 실행 중인지 체크합니다.
        boolean isRedisAvailable = false;
        try (Socket socket = new Socket("localhost", 6379)) {
            isRedisAvailable = true;
        } catch (Exception ignored) {
            // 레디스가 꺼져 있으면 소켓 연결 시 예외가 발생하므로 false 상태가 유지됩니다.
        }

        // 2. 이 조건이 false이면, JUnit은 FAILED 에러를 내지 않고 이 테스트를 안전하게 SKIP(건너뜀) 처리합니다.
        Assumptions.assumeTrue(isRedisAvailable, "로컬 Redis 서버가 실행 중이지 않으므로 테스트를 건너뜁니다.");
    }

    @Test
    @DisplayName("Redis 연결 및 데이터 읽기/쓰기 테스트")
    void redisReadWriteTest() {
        // Given
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String key = "test:key";
        String value = "hello-redis";

        // When
        valueOps.set(key, value);

        // Then
        String retrievedValue = valueOps.get(key);
        assertThat(retrievedValue).isEqualTo(value);

        // Clean up
        redisTemplate.delete(key);
    }
}