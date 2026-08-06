package com.back.global.redis

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

    @Value("\${spring.data.redis.host:localhost}")
    private lateinit var redisHost: String

    @Value("\${spring.data.redis.port:6379}")
    private var redisPort: Int = 0

    @Value("\${spring.data.redis.password:}")
    private lateinit var redisPassword: String

    /**
     * Spring Boot 4.x 및 코틀린 마이그레이션 호환성을 보장하기 위해
     * application.yml 설정을 기반으로 RedissonClient 빈을 수동으로 안전하게 기동합니다.
     */
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        val serverConfig = config.useSingleServer()
            .setAddress("redis://$redisHost:$redisPort")
        if (redisPassword.isNotBlank()) {
            serverConfig.password = redisPassword
        }
        return Redisson.create(config)
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory

        val stringSerializer = StringRedisSerializer()
        template.keySerializer = stringSerializer
        template.hashKeySerializer = stringSerializer
        template.valueSerializer = stringSerializer
        template.hashValueSerializer = stringSerializer

        return template
    }
}
