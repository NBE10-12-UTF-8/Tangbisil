package com.back.domain.match.scheduler;

import com.back.domain.match.matchRequest.entity.MatchingOutbox;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.member.member.entity.Industry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.back.global.exception.ServiceException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduler {

    private final MatchRequestService matchRequestService;
    private final RedisMatchQueue redisMatchQueue;
    private final MatchingOutboxRepository matchingOutboxRepository;

    /**
     * 10초마다 매칭 재시도를 수행합니다.
     * (Redis ZSet의 size가 0보다 큰 활성 대기열만 빠르게 스캔하여 처리합니다.)
     */
    @Scheduled(fixedDelay = 10000)
    public void retryPendingMatches() {
        for (Industry industry : Industry.values()) {
            for (Situation situation : Situation.values()) {
                // Redis ZSet의 대기 인원수(ZCARD)가 0명이면 즉시 패스하여 DB 조회를 차단합니다.
                if (redisMatchQueue.size(industry, situation) == 0) {
                    continue;
                }

                // 대기자가 존재하는 대기열에 한해서만 목록을 읽어옵니다.
                Set<String> allIds = redisMatchQueue.getAllIds(industry, situation);
                if (allIds != null) {
                    for (String idStr : allIds) {
                        try {
                            matchRequestService.tryMatch(UUID.fromString(idStr));
                        } catch (ServiceException e) {
                            if ("404-1".equals(e.getRsData().resultCode())) {
                                redisMatchQueue.remove(industry, situation, UUID.fromString(idStr));
                                log.info("[MatchScheduler] DB에 존재하지 않는 대기 요청 자동 정리 완료 - id: {}", idStr);
                            } else {
                                log.error("[MatchScheduler] 재매칭 처리 중 오류 발생 - matchRequestId: {}", idStr, e);
                            }
                        } catch (Exception e) {
                            log.error("[MatchScheduler] 재매칭 처리 중 오류 발생 - matchRequestId: {}", idStr, e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 10초마다 아웃박스 장부를 확인하여 Redis 적재가 실패한 대기 이벤트를 다시 ZADD로 재적재합니다.
     */
    @Scheduled(fixedDelay = 10000)
    public void retryOutboxEvents() {
        List<MatchingOutbox> failedEvents = matchingOutboxRepository.findByStatusInAndRetryCountLessThan(
                List.of(MatchingOutbox.OutboxStatus.INIT, MatchingOutbox.OutboxStatus.FAIL), 5);

        for (MatchingOutbox outbox : failedEvents) {
            try {
                // 원본 가중치(Score)를 그대로 사용하여 ZSet에 다시 적재 시도
                redisMatchQueue.add(
                        outbox.getIndustry(),
                        outbox.getSituation(),
                        outbox.getMatchRequestId(),
                        outbox.getRequestedAtEpochMilli()
                );
                // 적재 성공 시 SUCCESS로 아웃박스 변경
                outbox.markSuccess();
                matchingOutboxRepository.save(outbox);
                log.info("[MatchScheduler] 아웃박스 재적재 성공 - requestId: {}", outbox.getMatchRequestId());
            } catch (Exception e) {
                outbox.markFailed();
                matchingOutboxRepository.save(outbox);
                if (outbox.getRetryCount() >= 5) {
                    log.error("[CRITICAL] [MatchScheduler] 아웃박스 적재 실패 횟수 초과(5회). Redis 적재 포기됨 - requestId: {}",
                            outbox.getMatchRequestId(), e);
                } else {
                    log.error("[MatchScheduler] 아웃박스 재적재 실패 - requestId: {}, retryCount: {}",
                            outbox.getMatchRequestId(), outbox.getRetryCount(), e);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void cancelExpiredMatchRequests() {
        matchRequestService.cancelExpiredRequests();
    }
}