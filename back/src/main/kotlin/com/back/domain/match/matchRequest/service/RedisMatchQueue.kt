package com.back.domain.match.matchRequest.service

import com.back.domain.match.matchRequest.entity.Situation
import com.back.domain.member.member.entity.Industry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RedisMatchQueue(private val redisTemplate: RedisTemplate<String, String>) {

    companion object {
        private val log = LoggerFactory.getLogger(RedisMatchQueue::class.java)
        private const val QUEUE_KEY_PREFIX = "match:queue:"
    }

    fun add(industry: Industry, situation: Situation, matchRequestId: UUID, score: Long) {
        val key = getQueueKey(industry, situation)
        val value = matchRequestId.toString()
        try {
            redisTemplate.opsForZSet().add(key, value, score.toDouble())
            log.info("[RedisMatchQueue] 대기열 적재 완료 (ZADD) - key: {}, value: {}, score: {}", key, value, score)
        } catch (e: Exception) {
            log.error("[RedisMatchQueue] 대기열 적재 실패 - key: {}, value: {}", key, value, e)
            throw e
        }
    }

    fun remove(industry: Industry, situation: Situation, matchRequestId: UUID) {
        val key = getQueueKey(industry, situation)
        val value = matchRequestId.toString()
        try {
            redisTemplate.opsForZSet().remove(key, value)
            log.info("[RedisMatchQueue] 대기열 제거 완료 (ZREM) - key: {}, value: {}", key, value)
        } catch (e: Exception) {
            log.error("[RedisMatchQueue] 대기열 제거 실패 - key: {}, value: {}", key, value, e)
            throw e
        }
    }

    // limit만큼 페이징 조회 - 상위 2명만 보던 이전 방식은 그 2명이 막히면 뒤가 무기한 갇히는
    // Head-of-Line Blocking을 유발했다.
    fun getOldestCandidates(industry: Industry, situation: Situation, limit: Int): List<UUID> {
        val key = getQueueKey(industry, situation)
        try {
            val range = redisTemplate.opsForZSet().range(key, 0, (limit - 1).toLong())
            if (range.isNullOrEmpty()) {
                return emptyList()
            }
            return range.map { UUID.fromString(it) }
        } catch (e: Exception) {
            log.error("[RedisMatchQueue] 대기열 후보 조회 실패 (ZRANGE) - key: {}", key, e)
            throw e
        }
    }

    fun size(industry: Industry, situation: Situation): Long {
        val key = getQueueKey(industry, situation)
        try {
            return redisTemplate.opsForZSet().size(key) ?: 0L
        } catch (e: Exception) {
            log.error("[RedisMatchQueue] 대기열 크기 조회 실패 (ZCARD) - key: {}", key, e)
            throw e
        }
    }

    fun getAllIds(industry: Industry, situation: Situation): Set<String>? {
        val key = getQueueKey(industry, situation)
        try {
            return redisTemplate.opsForZSet().range(key, 0, -1)
        } catch (e: Exception) {
            log.error("[RedisMatchQueue] 대기열 전체 목록 조회 실패 (ZRANGE) - key: {}", key, e)
            throw e
        }
    }

    private fun getQueueKey(industry: Industry, situation: Situation): String =
        "$QUEUE_KEY_PREFIX${industry.name}:${situation.name}"
}
