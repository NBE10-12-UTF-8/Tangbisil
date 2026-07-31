package com.back.domain.trend.snapshot.scheduler;

import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

@Component
public class TrendSnapshotScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyKeywordCountRepository dailyKeywordCountRepository;
    private final DailyMessageCountRepository dailyMessageCountRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public TrendSnapshotScheduler(DailyKeywordCountRepository dailyKeywordCountRepository, DailyMessageCountRepository dailyMessageCountRepository, RedisTemplate<String, String> redisTemplate) {
        this.dailyKeywordCountRepository = dailyKeywordCountRepository;
        this.dailyMessageCountRepository = dailyMessageCountRepository;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void snapshotYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        String totalMessagesStr = redisTemplate.opsForValue().get("trend:messages:" + yesterday);

        if (totalMessagesStr == null) {
            return;
        }
        long totalMessages = Long.parseLong(totalMessagesStr);

        Optional<DailyMessageCount> existing = dailyMessageCountRepository.findByDate(yesterday);
        if (existing.isPresent()) {
            existing.get().updateTotalMessages(totalMessages);
        } else {
            dailyMessageCountRepository.save(new DailyMessageCount(yesterday, totalMessages));
        }

        Set<ZSetOperations.TypedTuple<String>> keywordScores =
                redisTemplate.opsForZSet().rangeWithScores("trend:keyword:" + yesterday, 0, -1);
        if (keywordScores == null) { keywordScores = Set.of(); }

        for (ZSetOperations.TypedTuple<String> tuple : keywordScores) {
            String keyword = tuple.getValue();
            long frequency = tuple.getScore().longValue();

            Optional<DailyKeywordCount> existingKeyword =
                    dailyKeywordCountRepository.findByDateAndKeyword(yesterday, keyword);
            if (existingKeyword.isPresent()) {
                existingKeyword.get().updateFrequency(frequency);
            } else {
                dailyKeywordCountRepository.save(new DailyKeywordCount(yesterday, keyword, frequency));
            }

        }
    }
}
