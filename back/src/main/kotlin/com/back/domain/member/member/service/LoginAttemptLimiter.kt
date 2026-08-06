package com.back.domain.member.member.service

import com.back.global.util.EmailNormalizer
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class LoginAttemptLimiter(
    private val redisTemplate: RedisTemplate<String, String>
) {
    companion object {
        private const val MAX_ATTEMPTS = 5
        private val WINDOW: Duration = Duration.ofMinutes(15)
        private const val KEY_PREFIX = "login:attempts:"
    }

    fun isBlocked(email: String): Boolean {
        val value = redisTemplate.opsForValue().get(key(email))
        return value != null && value.toLong() >= MAX_ATTEMPTS
    }

    fun recordFailure(email: String) {
        val key = key(email)
        val attempts = redisTemplate.opsForValue().increment(key)
        if (attempts != null && (attempts == 1L || redisTemplate.getExpire(key) < 0)) {
            redisTemplate.expire(key, WINDOW)
        }
    }

    fun recordSuccess(email: String) {
        redisTemplate.delete(key(email))
    }

    private fun key(email: String): String = KEY_PREFIX + EmailNormalizer.normalize(email)
}
