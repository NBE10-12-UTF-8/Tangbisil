package com.back.domain.match.matchRequest.service

import com.back.domain.bot.BotAccounts
import com.back.domain.bot.BotAccounts.isBotEmail
import com.back.domain.bot.BotReplyTriggerEvent
import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoom.service.ChatRoomService
import com.back.domain.match.matchRequest.dto.MatchHistoryDto
import com.back.domain.match.matchRequest.dto.SituationStatisticsDto
import com.back.domain.match.matchRequest.entity.MatchRequest
import com.back.domain.match.matchRequest.entity.MatchStatus
import com.back.domain.match.matchRequest.entity.MatchingOutbox
import com.back.domain.match.matchRequest.entity.Situation
import com.back.domain.match.matchRequest.entity.SituationSimilarity
import com.back.domain.match.matchRequest.event.MatchSuccessEvent
import com.back.domain.match.matchRequest.repository.MatchRequestRepository
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository
import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.entity.Member
import com.back.domain.member.member.repository.MemberRepository
import com.back.domain.notification.service.MatchNotificationService
import com.back.global.exception.ServiceException
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class MatchRequestService(
    private val matchRequestRepository: MatchRequestRepository,
    private val memberRepository: MemberRepository,
    private val chatRoomService: ChatRoomService,
    private val eventPublisher: ApplicationEventPublisher,
    private val retryProcessor: MatchRequestRetryProcessor,
    private val matchNotificationService: MatchNotificationService,
    private val redisMatchQueue: RedisMatchQueue,
    private val matchingOutboxRepository: MatchingOutboxRepository,
    private val redissonClient: RedissonClient,
    private val applicationContext: ApplicationContext
) {
    companion object {
        private val log = LoggerFactory.getLogger(MatchRequestService::class.java)

        private const val TIER1_THRESHOLD_SECONDS = 15L // 15초 후 유사 상황 매칭
        private const val TIER2_THRESHOLD_SECONDS = 30L // 30초 후 산업군 전체 매칭
        private const val BOT_FALLBACK_THRESHOLD_SECONDS = 35L // 그래도 못 찾으면 봇 폴백
        private const val CANDIDATE_PAGE_SIZE = 50 // ZSet 상위 몇 명까지 후보로 볼지 (Head-of-Line Blocking 방지)
        private const val BATCH_MAX_ITERATIONS = 200 // 락 하나로 연속 매칭하는 배치 루프 반복 상한
    }

    // 후보 UUID가 어느 situation 큐에서 나왔는지 함께 들고 다니기 위한 자료구조 (자가 치유 ZREM에 필요).
    private data class CandidateRef(val situation: Situation, val id: UUID)

    private fun self(): MatchRequestService = applicationContext.getBean(MatchRequestService::class.java)

    private fun connect(matchRequest: MatchRequest, other: MatchRequest) {
        val chatRoom: ChatRoom = chatRoomService.createChatRoom(listOf(matchRequest.member, other.member))
        // 영속 상태에서 필드만 바꿔 더티 체킹으로 커밋 시 room/status를 함께 반영한다.
        matchRequest.matchWith(chatRoom)
        other.matchWith(chatRoom)
        triggerBotReplyIfNeeded(matchRequest.member, other.member, chatRoom.id)
        eventPublisher.publishEvent(MatchSuccessEvent(chatRoom.uuid, matchRequest.member.id, other.member.id))
    }

    // 봇 폴백 - 실제 유저 매칭 기회를 먼저 주기 위해 봇은 평소엔 대기열에 없다.
    private fun matchWithBot(request: MatchRequest) {
        val industry = request.member.industry
        val bot = memberRepository.findByEmail(BotAccounts.emailFor(industry)).orElse(null)
        if (bot == null) {
            log.error("[MatchRequestService] {} 산업군 봇 계정을 찾을 수 없음", industry)
            return
        }

        val botRequest = matchRequestRepository.save(MatchRequest(bot, request.situation))
        connect(request, botRequest)
    }

    private fun triggerBotReplyIfNeeded(requester: Member, other: Member, roomId: Long) {
        if (other.email.isBotEmail()) {
            eventPublisher.publishEvent(BotReplyTriggerEvent(roomId, other.id))
        } else if (requester.email.isBotEmail()) {
            eventPublisher.publishEvent(BotReplyTriggerEvent(roomId, requester.id))
        }
    }

    @Transactional
    fun create(member: Member, situation: Situation): MatchRequest {
        if (member.industry == null) {
            throw ServiceException("400-2", "산업군이 설정되지 않은 계정은 매칭을 요청할 수 없습니다.")
        }
        if (matchRequestRepository.existsByMemberAndStatus(member, MatchStatus.PENDING)) {
            throw ServiceException("409-1", "이미 진행 중인 매칭 요청이 있습니다.")
        }

        val matchRequest = matchRequestRepository.save(MatchRequest(member, situation))

        val epochMilli = matchRequest.requestedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val outbox = MatchingOutbox.create(matchRequest.uuid, member.industry, situation, epochMilli)
        matchingOutboxRepository.save(outbox)

        // 커밋 완료 직후 Redis ZADD + 1차 매칭 시도. 성공 경로는 커넥션을 추가로 잡지 않고,
        // 아웃박스 SUCCESS 마킹도 하지 않는다 - retryOutboxEvents 스케줄러가 뒤늦게 보정한다.
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                try {
                    redisMatchQueue.add(member.industry, situation, matchRequest.uuid, epochMilli)
                } catch (e: Exception) {
                    // 실패는 드문 경로이므로 여기서만 REQUIRES_NEW로 즉시 FAIL 마킹한다.
                    self().markOutboxFailed(outbox.id)
                    log.error("[MatchRequestService] Redis 대기열 적재 실패 - requestId: {}", matchRequest.id, e)
                    return
                }
                try {
                    retryProcessor.retryOne(matchRequest.uuid, member.industry)
                } catch (e: Exception) {
                    log.error("[MatchRequestService] 1차 즉시 매칭 시도 중 오류 발생 - matchRequestId: {}", matchRequest.id, e)
                }
            }
        })

        return matchRequest
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markOutboxFailed(outboxId: Long) {
        matchingOutboxRepository.findById(outboxId).ifPresent { it.markFailed() }
    }

    /**
     * 지정된 Industry의 분산 락을 시도하여 1차 비동기 매칭 및 연속 배치 매칭을 처리합니다.
     * 주의: 호출부는 반드시 대상 요청의 올바른 Industry를 전달해야 합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun tryMatch(matchRequestId: UUID, industry: Industry, waitTimeSeconds: Long) {
        val lockKey = "match:lock:${industry.name}"
        val lock = redissonClient.getLock(lockKey)
        try {
            // leaseTime 미지정 -> Redisson watchdog이 붙어 배치 루프가 길어져도 락이 새지 않는다.
            if (lock.tryLock(waitTimeSeconds, TimeUnit.SECONDS)) {
                try {
                    self().processMatch(matchRequestId, industry)
                } finally {
                    if (lock.isHeldByCurrentThread) {
                        lock.unlock()
                    }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ServiceException("500-1", "분산 락 획득 중 인터럽트가 발생했습니다.")
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun tryMatch(matchRequestId: UUID, industry: Industry) {
        tryMatch(matchRequestId, industry, 10)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun tryMatch(matchRequestId: UUID) {
        val matchRequest = matchRequestRepository.findByUuidWithMember(matchRequestId)
            ?: throw ServiceException("404-1", "매칭 요청을 찾을 수 없습니다.")
        if (matchRequest.status != MatchStatus.PENDING) {
            return
        }
        tryMatch(matchRequestId, matchRequest.member.industry)
    }

    // self-invocation은 프록시를 안 거쳐 위 NOT_SUPPORTED가 적용 안 되므로, 이 메서드도 동일하게 막아둔다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun tryMatch(matchRequestParam: MatchRequest) {
        tryMatch(matchRequestParam.uuid)
    }

    // 분산 락을 쥔 상태에서 대기자를 연속으로 이어 매칭하는 배치 루프. 최초 트리거된 요청을 먼저
    // 처리하고, 이후로는 매번 industry 전체에서 가장 오래 기다린 PENDING 요청을 다시 골라 이어간다.
    // 이 메서드 자체는 NOT_SUPPORTED - 실제 DB 쓰기는 connectPair()/connectWithBot()의 개별
    // REQUIRES_NEW에 위임해서, 한 쌍 커밋이 다른 쌍에 영향을 주지 않게 격리한다. Redis 제거는
    // 그 커밋이 성공한 뒤에만 best-effort로 수행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun processMatch(matchRequestId: UUID, industry: Industry) {
        var currentTargetId: UUID? = matchRequestId
        var iterations = 0

        while (currentTargetId != null && iterations++ < BATCH_MAX_ITERATIONS) {
            val current = matchRequestRepository.findByUuidWithMember(currentTargetId)
            if (current == null) {
                if (iterations == 1) {
                    throw ServiceException("404-1", "매칭 요청을 찾을 수 없습니다.")
                }
                break
            }
            if (current.status != MatchStatus.PENDING) {
                break
            }

            val situation = current.situation
            val elapsedSeconds = Duration.between(current.requestedAt, LocalDateTime.now()).seconds

            val opponent = try {
                findOpponent(current, elapsedSeconds)
            } catch (e: Exception) {
                log.error("[MatchRequestService] 배치 매칭 루프 중 후보 탐색 오류 발생 - matchRequestId: {}", currentTargetId, e)
                break
            }

            if (opponent != null) {
                val opponentId = opponent.uuid
                val opponentSituation = opponent.situation
                try {
                    self().connectPair(current.uuid, opponentId)
                } catch (e: Exception) {
                    log.error("[MatchRequestService] 매칭 확정 실패 - currentId: {}, opponentId: {}", current.uuid, opponentId, e)
                    break
                }
                safeRemoveFromQueue(industry, situation, current.uuid)
                safeRemoveFromQueue(industry, opponentSituation, opponentId)
            } else if (elapsedSeconds >= BOT_FALLBACK_THRESHOLD_SECONDS) {
                try {
                    self().connectWithBot(current.uuid)
                } catch (e: Exception) {
                    log.error("[MatchRequestService] 봇 매칭 확정 실패 - matchRequestId: {}", currentTargetId, e)
                    break
                }
                safeRemoveFromQueue(industry, situation, current.uuid)
            } else {
                break
            }

            currentTargetId = findOldestInSituations(industry, Situation.entries, null)?.uuid
        }
    }

    // 매칭 확정 한 쌍만의 최소 단위 트랜잭션 - 이후 회차 실패가 이미 커밋된 쌍을 함께 롤백시키지 않도록 격리한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun connectPair(currentId: UUID, opponentId: UUID) {
        val current = matchRequestRepository.findByUuidWithMember(currentId)
            ?: throw ServiceException("404-1", "매칭 요청을 찾을 수 없습니다.")
        val opponent = matchRequestRepository.findByUuidWithMember(opponentId)
            ?: throw ServiceException("404-1", "매칭 요청을 찾을 수 없습니다.")
        if (current.status != MatchStatus.PENDING || opponent.status != MatchStatus.PENDING) {
            throw ServiceException("409-1", "이미 처리된 매칭 요청입니다.")
        }
        connect(current, opponent)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun connectWithBot(currentId: UUID) {
        val current = matchRequestRepository.findByUuidWithMember(currentId)
            ?: throw ServiceException("404-1", "매칭 요청을 찾을 수 없습니다.")
        if (current.status != MatchStatus.PENDING) {
            throw ServiceException("409-1", "이미 처리된 매칭 요청입니다.")
        }
        matchWithBot(current)
    }

    // ZREM 실패는 치명적이지 않다 - findOldestInSituations()의 자가 치유 로직이 다음 스캔에서 정리한다.
    private fun safeRemoveFromQueue(industry: Industry, situation: Situation, id: UUID) {
        try {
            redisMatchQueue.remove(industry, situation, id)
        } catch (e: Exception) {
            log.warn("[MatchRequestService] Redis 대기열 정리 실패(자가 치유 로직이 추후 정리) - id: {}", id, e)
        }
    }

    // 상황 조건(Tier 1~3)에 맞는 대기열들을 찾아 가장 대기 시간이 오래된 상대를 탐색합니다.
    private fun findOpponent(request: MatchRequest, elapsedSeconds: Long): MatchRequest? {
        val industry = request.member.industry
        val situation = request.situation
        val excludeId = request.uuid
        if (elapsedSeconds < TIER1_THRESHOLD_SECONDS) {
            return findOldestInSituations(industry, listOf(situation), excludeId)
        }

        if (elapsedSeconds < TIER2_THRESHOLD_SECONDS) {
            val similarGroup = SituationSimilarity.getSimilarGroup(situation)
            return findOldestInSituations(industry, similarGroup, excludeId)
        }

        return findOldestInSituations(industry, Situation.entries, excludeId)
    }

    /**
     * 지정된 상황들의 Redis ZSet 상위 [CANDIDATE_PAGE_SIZE]명 중 가장 오래 기다린 PENDING
     * 사용자 1명을 선출합니다. 후보 UUID를 먼저 모두 모아 IN절로 일괄 조회하고(N+1 방지), 스테일
     * 항목은 파이프라인 밖에서 별도로 ZREM 정리합니다(자가 치유).
     */
    private fun findOldestInSituations(
        industry: Industry,
        situations: Collection<Situation>,
        excludeId: UUID?
    ): MatchRequest? {
        val candidateRefs = situations.flatMap { s ->
            redisMatchQueue.getOldestCandidates(industry, s, CANDIDATE_PAGE_SIZE)
                .filter { it != excludeId }
                .map { CandidateRef(s, it) }
        }

        if (candidateRefs.isEmpty()) {
            return null
        }

        val candidateIds = candidateRefs.map { it.id }
        val byId = matchRequestRepository.findAllByUuidIn(candidateIds).associateBy { it.uuid }

        for (ref in candidateRefs) {
            val mr = byId[ref.id]
            if (mr == null || mr.status != MatchStatus.PENDING) {
                redisMatchQueue.remove(industry, ref.situation, ref.id)
            }
        }

        // requestedAt 동점 시 id를 2차 기준으로 더해 항상 같은 후보를 확정적으로 고른다 (낙관적 락 충돌 방지).
        return byId.values
            .filter { it.status == MatchStatus.PENDING }
            .minWithOrNull(compareBy({ it.requestedAt }, { it.id }))
    }

    fun findById(id: UUID): MatchRequest =
        matchRequestRepository.findByUuid(id) ?: throw ServiceException("404-1", "매칭 요청을 찾을 수 없습니다.")

    @Transactional
    fun cancel(matchRequest: MatchRequest, actor: Member) {
        if (matchRequest.member.id != actor.id) {
            throw ServiceException("403-1", "접근 권한이 없습니다.")
        }

        // deleteByIdAndStatus가 clearAutomatically=true라 실행 후 matchRequest가 detach되어
        // lazy 필드(member)에 다시 접근할 수 없다 - 필요한 값은 미리 꺼내둔다.
        val industry = matchRequest.member.industry
        val situation = matchRequest.situation
        val uuid = matchRequest.uuid

        // status를 먼저 SELECT해서 앱 코드에서 확인하면, 그 사이 매칭 배치(processMatch)가
        // 이 요청을 PENDING으로 읽어 확정시켜버려도 걸러내지 못한다(이 체크가 이미 지난
        // 스냅샷 기준이라서). DELETE 문 자체에 status 조건을 걸어 DB가 최신 커밋 상태
        // 기준으로 원자적으로 처리하게 한다.
        val deleted = matchRequestRepository.deleteByIdAndStatus(matchRequest.id, MatchStatus.PENDING)
        if (deleted == 0) {
            throw ServiceException("409-1", "이미 매칭된 요청은 취소할 수 없습니다.")
        }

        redisMatchQueue.remove(industry, situation, uuid)
    }

    @Transactional
    fun cancelExpiredRequests() {
        val expiredBefore = LocalDateTime.now().minusMinutes(5)
        val expired = matchRequestRepository.findExpiredPending(MatchStatus.PENDING, expiredBefore)

        for (request in expired) {
            redisMatchQueue.remove(request.member.industry, request.situation, request.uuid)
        }

        matchRequestRepository.deleteAll(expired)
    }

    fun findMatchHistoryByMember(member: Member): List<MatchHistoryDto> {
        val requests = matchRequestRepository.findByMemberAndRoomStatus(member, ChatRoomStatus.CLOSED)

        val roomIds = requests.map { it.room!!.id }.distinct()

        val botMap = chatRoomService.hasBotParticipantMap(roomIds)

        return requests.map { MatchHistoryDto(it, botMap.getOrDefault(it.room!!.id, false)) }
    }

    @Transactional(readOnly = true)
    fun getSituationStatistics(): List<SituationStatisticsDto> =
        matchRequestRepository.countActiveBySituation(MatchStatus.MATCHED, ChatRoomStatus.ACTIVE)

    fun hasPendingRequest(member: Member): Boolean =
        matchRequestRepository.existsByMemberAndStatus(member, MatchStatus.PENDING)

    fun findOpponentSituation(roomId: Long, memberId: Long): Situation? =
        matchRequestRepository.findByRoomIdAndMemberIdNot(roomId, memberId, PageRequest.of(0, 1))
            .firstOrNull()?.situation

    @Transactional
    fun deleteAllByMember(member: Member) {
        matchRequestRepository.deleteByMember(member)
    }
}
