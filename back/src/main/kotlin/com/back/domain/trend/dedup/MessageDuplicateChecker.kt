package com.back.domain.trend.dedup

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

@Component
class MessageDuplicateChecker(
    private val redisTemplate: RedisTemplate<String, String>
) {
    companion object {
        private val KEY_TTL: Duration = Duration.ofDays(2)
        private const val FINGERPRINT_KEY_PREFIX = "trend:fingerprints:"
        private val WHITESPACE_PATTERN = Regex("\\s+")
    }

    fun isDuplicate(date: LocalDate, senderMemberId: UUID?, content: String?): Boolean {
        if (senderMemberId == null || content == null || content.isBlank()) {
            return false
        }

        val key = FINGERPRINT_KEY_PREFIX + date
        val added = redisTemplate.opsForSet().add(key, fingerprint(senderMemberId, content))
        if (added != null && added > 0L) {
            redisTemplate.expire(key, KEY_TTL)
        }

        return added != null && added == 0L
    }

    private fun fingerprint(senderMemberId: UUID, content: String): String {
        val normalized = "$senderMemberId|${WHITESPACE_PATTERN.replace(content, "").lowercase(Locale.ROOT)}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }
}
