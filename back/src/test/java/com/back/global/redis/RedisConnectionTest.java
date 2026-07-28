package com.back.global.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("Redis 연결 및 데이터 읽기/쓰기 테스트")
    void redisReadWriteTest() {
        // Given
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue(); // 제네릭 일치
        String key = "test:key";
        String value = "hello-redis";

        // When (데이터 저장)
        valueOps.set(key, value);

        // Then (데이터 조회 및 검증)
        String retrievedValue = valueOps.get(key); // 리턴 타입을 String으로 받아옴
        assertThat(retrievedValue).isEqualTo(value);

        // Clean up (테스트 데이터 삭제)
        redisTemplate.delete(key);
    }
}