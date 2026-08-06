package com.back.domain.trend.snapshot.service

import com.back.domain.trend.snapshot.entity.DailyCooccurrenceCount
import com.back.domain.trend.snapshot.entity.DailyKeywordCount
import com.back.domain.trend.snapshot.entity.DailyMessageCount
import com.back.domain.trend.snapshot.repository.DailyCooccurrenceCountRepository
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

// TrendSnapshotScheduler에서 실제 스냅샷 로직만 분리한 클래스.
// @Transactional은 프록시를 거치는 외부 호출에만 걸리므로, 스케줄러 안에서
// this.snapshotDate(...)처럼 자기 자신을 직접 호출하면 트랜잭션이 조용히 무시된다 —
// 그래서 별도 빈으로 분리해 스케줄러가 프록시를 통해 호출하도록 한다.
@Service
class TrendSnapshotService(
    private val dailyKeywordCountRepository: DailyKeywordCountRepository,
    private val dailyMessageCountRepository: DailyMessageCountRepository,
    private val dailyCooccurrenceCountRepository: DailyCooccurrenceCountRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {
    @Transactional
    fun snapshotDate(date: LocalDate) {
        val totalMessagesStr = redisTemplate.opsForValue().get("trend:messages:$date") ?: return
        val totalMessages = totalMessagesStr.toLong()

        val existingMessageCount = dailyMessageCountRepository.findByDate(date)
        if (existingMessageCount != null) {
            existingMessageCount.updateTotalMessages(totalMessages)
        } else {
            dailyMessageCountRepository.save(DailyMessageCount(date, totalMessages))
        }

        val keywordScores = redisTemplate.opsForZSet().rangeWithScores("trend:keyword:$date", 0, -1) ?: emptySet()

        val existingKeywords = dailyKeywordCountRepository.findAllByDate(date)
            .associateBy { it.keyword }

        for (tuple in keywordScores) {
            val keyword = tuple.value ?: continue
            val frequency = tuple.score?.toLong() ?: continue

            val existingKeyword: DailyKeywordCount? = existingKeywords[keyword]
            if (existingKeyword != null) {
                existingKeyword.updateFrequency(frequency)
            } else {
                dailyKeywordCountRepository.save(DailyKeywordCount(date, keyword, frequency))
            }
        }

        val cooccurScores = redisTemplate.opsForZSet().rangeWithScores("trend:cooccur:$date", 0, -1) ?: emptySet()
        val existingCooccurrences = dailyCooccurrenceCountRepository.findAllByDate(date)
            .associateBy { "${it.keywordA}::${it.keywordB}" }

        for (tuple in cooccurScores) {
            val pair = tuple.value ?: continue
            val frequency = tuple.score?.toLong() ?: continue
            val parts = pair.split("::")
            if (parts.size < 2) {
                continue
            }
            val existingCooccurrence: DailyCooccurrenceCount? = existingCooccurrences[pair]
            if (existingCooccurrence != null) {
                existingCooccurrence.updateFrequency(frequency)
            } else {
                dailyCooccurrenceCountRepository.save(DailyCooccurrenceCount(date, parts[0], parts[1], frequency))
            }
        }
    }
}
