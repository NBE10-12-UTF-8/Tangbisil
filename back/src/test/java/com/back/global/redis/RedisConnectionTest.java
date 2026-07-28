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
        try (Socket socket = new Socket()) {
            // 1초(1000ms)의 연결 타임아웃을 강제하여 무한 대기(블로킹)를 방지합니다.
            socket.connect(new java.net.InetSocketAddress(redisHost, redisPort), 1000);
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

        try {
            valueOps.set(key, value);
            String retrievedValue = valueOps.get(key);
            assertThat(retrievedValue).isEqualTo(value);
        } finally {
            redisTemplate.delete(key);
        }
    }
}