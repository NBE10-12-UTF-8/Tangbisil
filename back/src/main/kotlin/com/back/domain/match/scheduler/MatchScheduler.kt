package com.back.domain.match.scheduler

import com.back.domain.match.matchRequest.entity.MatchStatus
import com.back.domain.match.matchRequest.entity.MatchingOutbox
import com.back.domain.match.matchRequest.entity.Situation
import com.back.domain.match.matchRequest.repository.MatchRequestRepository
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository
import com.back.domain.match.matchRequest.service.MatchRequestService
import com.back.domain.match.matchRequest.service.RedisMatchQueue
import com.back.domain.member.member.entity.Industry
import com.back.global.exception.ServiceException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MatchScheduler(
    private val matchRequestService: MatchRequestService,
    private val redisMatchQueue: RedisMatchQueue,
    private val matchingOutboxRepository: MatchingOutboxRepository,
    private val matchRequestRepository: MatchRequestRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(MatchScheduler::class.java)
    }

    /**
     * 10초마다 매칭 재시도를 수행합니다.
     * (Redis ZSet의 size가 0보다 큰 활성 대기열만 빠르게 스캔하여 처리합니다.)
     */
    @Scheduled(fixedDelay = 10000)
    fun retryPendingMatches() {
        // Industry는 아직 Java enum이라 Kotlin의 .entries 대신 .values()를 쓴다.
        for (industry in Industry.values()) {
            for (situation in Situation.entries) {
                // Redis ZSet의 대기 인원수(ZCARD)가 0명이면 즉시 패스하여 DB 조회를 차단합니다.
                if (redisMatchQueue.size(industry, situation) == 0L) {
                    continue
                }

                // 대기자가 존재하는 대기열에 한해서만 목록을 읽어옵니다.
                val allIds = redisMatchQueue.getAllIds(industry, situation)
                if (allIds != null) {
                    for (idStr in allIds) {
                        try {
                            // 스케줄러는 락 경합 시 대기 없이(0초) 즉시 통과하여 스레드 블로킹을 방지합니다.
                            matchRequestService.tryMatch(UUID.fromString(idStr), industry, 0)
                        } catch (e: ServiceException) {
                            if (e.rsData.resultCode == "404-1") {
                                redisMatchQueue.remove(industry, situation, UUID.fromString(idStr))
                                log.info("[MatchScheduler] DB에 존재하지 않는 대기 요청 자동 정리 완료 - id: {}", idStr)
                            } else {
                                log.error("[MatchScheduler] 재매칭 처리 중 오류 발생 - matchRequestId: {}", idStr, e)
                            }
                        } catch (e: Exception) {
                            log.error("[MatchScheduler] 재매칭 처리 중 오류 발생 - matchRequestId: {}", idStr, e)
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
    fun retryOutboxEvents() {
        val failedEvents = matchingOutboxRepository.findByStatusInAndRetryCountLessThan(
            listOf(MatchingOutbox.OutboxStatus.INIT, MatchingOutbox.OutboxStatus.FAIL), 5
        )

        for (outbox in failedEvents) {
            try {
                val matchRequest = matchRequestRepository.findByUuid(outbox.matchRequestId)
                if (matchRequest == null || matchRequest.status != MatchStatus.PENDING) {
                    outbox.markSuccess()
                    matchingOutboxRepository.save(outbox)
                    log.info("[MatchScheduler] 취소 또는 이미 처리 완료된 요청의 아웃박스 이벤트 종결 처리 - requestId: {}", outbox.matchRequestId)
                    continue
                }

                // 원본 가중치(Score)를 그대로 사용하여 ZSet에 다시 적재 시도
                redisMatchQueue.add(outbox.industry, outbox.situation, outbox.matchRequestId, outbox.requestedAtEpochMilli)
                // 적재 성공 시 SUCCESS로 아웃박스 변경
                outbox.markSuccess()
                matchingOutboxRepository.save(outbox)
                log.info("[MatchScheduler] 아웃박스 재적재 성공 - requestId: {}", outbox.matchRequestId)
            } catch (e: Exception) {
                outbox.markFailed()
                matchingOutboxRepository.save(outbox)
                if (outbox.retryCount >= 5) {
                    log.error(
                        "[CRITICAL] [MatchScheduler] 아웃박스 적재 실패 횟수 초과(5회). Redis 적재 포기됨 - requestId: {}",
                        outbox.matchRequestId, e
                    )
                } else {
                    log.error(
                        "[MatchScheduler] 아웃박스 재적재 실패 - requestId: {}, retryCount: {}",
                        outbox.matchRequestId, outbox.retryCount, e
                    )
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    fun cancelExpiredMatchRequests() {
        matchRequestService.cancelExpiredRequests()
    }
}
