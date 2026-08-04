package com.back.domain.trend.snapshot.service;

import com.back.domain.trend.snapshot.entity.DailyCooccurrenceCount;
import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyCooccurrenceCountRepository;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// TrendSnapshotScheduler에서 실제 스냅샷 로직만 분리한 클래스.
// @Transactional은 프록시를 거치는 외부 호출에만 걸리므로, 스케줄러 안에서
// this.snapshotDate(...)처럼 자기 자신을 직접 호출하면 트랜잭션이 조용히 무시된다 —
// 그래서 별도 빈으로 분리해 스케줄러가 프록시를 통해 호출하도록 한다.
@Service
public class TrendSnapshotService {

    private final DailyKeywordCountRepository dailyKeywordCountRepository;
    private final DailyMessageCountRepository dailyMessageCountRepository;
    private final DailyCooccurrenceCountRepository dailyCooccurrenceCountRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public TrendSnapshotService(DailyKeywordCountRepository dailyKeywordCountRepository,
                                 DailyMessageCountRepository dailyMessageCountRepository,
                                 DailyCooccurrenceCountRepository dailyCooccurrenceCountRepository,
                                 RedisTemplate<String, String> redisTemplate) {
        this.dailyKeywordCountRepository = dailyKeywordCountRepository;
        this.dailyMessageCountRepository = dailyMessageCountRepository;
        this.dailyCooccurrenceCountRepository = dailyCooccurrenceCountRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void snapshotDate(LocalDate date) {
        String totalMessagesStr = redisTemplate.opsForValue().get("trend:messages:" + date);

        if (totalMessagesStr == null) {
            return;
        }
        long totalMessages = Long.parseLong(totalMessagesStr);

        Optional<DailyMessageCount> existing = dailyMessageCountRepository.findByDate(date);
        if (existing.isPresent()) {
            existing.get().updateTotalMessages(totalMessages);
        } else {
            dailyMessageCountRepository.save(new DailyMessageCount(date, totalMessages));
        }

        Set<ZSetOperations.TypedTuple<String>> keywordScores =
                redisTemplate.opsForZSet().rangeWithScores("trend:keyword:" + date, 0, -1);
        if (keywordScores == null) { keywordScores = Set.of(); }

        Map<String, DailyKeywordCount> existingKeywords = dailyKeywordCountRepository.findAllByDate(date)
                .stream()
                .collect(Collectors.toMap(DailyKeywordCount::getKeyword, Function.identity()));

        for (ZSetOperations.TypedTuple<String> tuple : keywordScores) {
            String keyword = tuple.getValue();
            long frequency = tuple.getScore().longValue();

            DailyKeywordCount existingKeyword = existingKeywords.get(keyword);
            if (existingKeyword != null) {
                existingKeyword.updateFrequency(frequency);
            } else {
                dailyKeywordCountRepository.save(new DailyKeywordCount(date, keyword, frequency));
            }
        }

        Set<ZSetOperations.TypedTuple<String>> cooccurScores =
                redisTemplate.opsForZSet().rangeWithScores("trend:cooccur:" + date, 0, -1);
        if (cooccurScores == null) { cooccurScores = Set.of(); }
        Map<String, DailyCooccurrenceCount> existingCooccurrences = dailyCooccurrenceCountRepository.findAllByDate(date)
                .stream()
                .collect(Collectors.toMap(row -> row.getKeywordA() + "::" + row.getKeywordB(), Function.identity()));
        for (ZSetOperations.TypedTuple<String> tuple : cooccurScores) {
            String pair = tuple.getValue();
            long frequency = tuple.getScore().longValue();
            String[] parts = pair.split("::");
            if (parts.length < 2) {
                continue;
            }
            DailyCooccurrenceCount existingCooccurrence = existingCooccurrences.get(pair);
            if (existingCooccurrence != null) {
                existingCooccurrence.updateFrequency(frequency);
            } else {
                dailyCooccurrenceCountRepository.save(new DailyCooccurrenceCount(date, parts[0], parts[1], frequency));
            }
        }
    }
}
