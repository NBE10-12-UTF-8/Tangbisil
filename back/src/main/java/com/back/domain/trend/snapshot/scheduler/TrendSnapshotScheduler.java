package com.back.domain.trend.snapshot.scheduler;

import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TrendSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrendSnapshotScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyKeywordCountRepository dailyKeywordCountRepository;
    private final DailyMessageCountRepository dailyMessageCountRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public TrendSnapshotScheduler(DailyKeywordCountRepository dailyKeywordCountRepository, DailyMessageCountRepository dailyMessageCountRepository, RedisTemplate<String, String> redisTemplate) {
        this.dailyKeywordCountRepository = dailyKeywordCountRepository;
        this.dailyMessageCountRepository = dailyMessageCountRepository;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void snapshotYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        try {
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

            Map<String, DailyKeywordCount> existingKeywords = dailyKeywordCountRepository.findAllByDate(yesterday)
                    .stream()
                    .collect(Collectors.toMap(DailyKeywordCount::getKeyword, Function.identity()));

            for (ZSetOperations.TypedTuple<String> tuple : keywordScores) {
                String keyword = tuple.getValue();
                long frequency = tuple.getScore().longValue();

                DailyKeywordCount existingKeyword = existingKeywords.get(keyword);
                if (existingKeyword != null) {
                    existingKeyword.updateFrequency(frequency);
                } else {
                    dailyKeywordCountRepository.save(new DailyKeywordCount(yesterday, keyword, frequency));
                }

            }
        } catch (Exception e) {
            // 이 배치가 실패해도 다음 스케줄 실행 자체는 끊기지 않지만(Spring 기본 동작),
            // 어제 날짜는 다시 스냅샷되지 않으므로 실패를 놓치지 않도록 로그를 남긴다.
            log.error("트렌드 스냅샷 실패 - date={}", yesterday, e);
        }
    }
}
