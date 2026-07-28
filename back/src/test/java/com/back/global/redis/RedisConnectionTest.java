package com.back.global.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @BeforeEach
    void setUp() {
        boolean isRedisAvailable = false;
        try (Socket socket = new Socket(redisHost, redisPort)) {
            // socket 객체 미사용 경고를 제거하기 위해 연결 상태 메소드 호출을 명시합니다.
            isRedisAvailable = socket.isConnected();
        } catch (Exception ignored) {
        }

        Assumptions.assumeTrue(isRedisAvailable, "로컬 Redis 서버가 실행 중이지 않으므로 테스트를 건너뜁니다.");
    }

    @Test
    @DisplayName("Redis 연결 및 데이터 읽기/쓰기 테스트")
    void redisReadWriteTest() {
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        String key = "test:key";
        String value = "hello-redis";

        valueOps.set(key, value);

        String retrievedValue = valueOps.get(key);
        assertThat(retrievedValue).isEqualTo(value);

        redisTemplate.delete(key);
    }
}