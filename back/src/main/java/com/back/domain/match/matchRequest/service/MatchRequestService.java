package com.back.domain.match.matchRequest.service;

import com.back.domain.bot.BotAccounts;
import com.back.domain.bot.BotReplyTriggerEvent;
import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.service.ChatRoomService;
import com.back.domain.match.matchRequest.dto.MatchHistoryDto;
import com.back.domain.match.matchRequest.dto.SituationStatisticsDto;
import com.back.domain.match.matchRequest.entity.*;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.notification.service.MatchNotificationService;
import com.back.global.exception.ServiceException;
import com.back.domain.match.matchRequest.event.MatchSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;
import java.time.ZoneId;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchRequestService {
    private final MatchRequestRepository matchRequestRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomService chatRoomService;
    private final ApplicationEventPublisher eventPublisher;
    private final MatchRequestRetryProcessor retryProcessor;
    private final MatchNotificationService matchNotificationService;
    private final RedisMatchQueue redisMatchQueue;
    private final MatchingOutboxRepository matchingOutboxRepository;
    private final RedissonClient redissonClient;
    private final ApplicationContext applicationContext;

    private static final long TIER1_THRESHOLD_SECONDS = 15; // 15초 후 유사 상황 매칭
    private static final long TIER2_THRESHOLD_SECONDS = 30; // 30초 후 산업군 전체 매칭
    private static final long BOT_FALLBACK_THRESHOLD_SECONDS = 35; // 그래도 못 찾으면 봇 폴백
    private static final int CANDIDATE_PAGE_SIZE = 50; // ZSet 상위 몇 명까지 후보로 볼지 (Head-of-Line Blocking 방지)
    private static final int BATCH_MAX_ITERATIONS = 200; // 락 하나로 연속 매칭하는 배치 루프 반복 상한

    private void connect(MatchRequest matchRequest, MatchRequest other) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(List.of(matchRequest.getMember(), other.getMember()));
        // 영속 상태에서 필드만 바꿔 더티 체킹으로 커밋 시 room/status를 함께 반영한다.
        matchRequest.matchWith(chatRoom);
        other.matchWith(chatRoom);
        triggerBotReplyIfNeeded(matchRequest.getMember(), other.getMember(), chatRoom.getId());
        eventPublisher.publishEvent(new MatchSuccessEvent(chatRoom.getUuid(), matchRequest.getMember().getId(), other.getMember().getId()));
    }

    // 봇 폴백 - 실제 유저 매칭 기회를 먼저 주기 위해 봇은 평소엔 대기열에 없다.
    private void matchWithBot(MatchRequest request) {
        Industry industry = request.getMember().getIndustry();
        Member bot = memberRepository.findByEmail(BotAccounts.emailFor(industry)).orElse(null);
        if (bot == null) {
            log.error("[MatchRequestService] {} 산업군 봇 계정을 찾을 수 없음", industry);
            return;
        }

        MatchRequest botRequest = matchRequestRepository.save(new MatchRequest(bot, request.getSituation()));
        connect(request, botRequest);
    }

    private void triggerBotReplyIfNeeded(Member requester, Member other, Long roomId) {
        if (BotAccounts.isBotEmail(other.getEmail())) {
            eventPublisher.publishEvent(new BotReplyTriggerEvent(roomId, other.getId()));
        } else if (BotAccounts.isBotEmail(requester.getEmail())) {
            eventPublisher.publishEvent(new BotReplyTriggerEvent(roomId, requester.getId()));
        }
    }

    @Transactional
    public MatchRequest create(Member member, Situation situation) {
        if (member.getIndustry() == null) {
            throw new ServiceException("400-2", "산업군이 설정되지 않은 계정은 매칭을 요청할 수 없습니다.");
        }
        if (matchRequestRepository.existsByMemberAndStatus(member, MatchStatus.PENDING)) {
            throw new ServiceException("409-1", "이미 진행 중인 매칭 요청이 있습니다.");
        }

        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, situation));

        long epochMilli = matchRequest.getRequestedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        MatchingOutbox outbox = MatchingOutbox.create(
                matchRequest.getUuid(),
                member.getIndustry(),
                situation,
                epochMilli
        );
        matchingOutboxRepository.save(outbox);

        // 커밋 완료 직후 Redis ZADD + 1차 매칭 시도. 성공 경로는 커넥션을 추가로 잡지 않고,
        // 아웃박스 SUCCESS 마킹도 하지 않는다 - retryOutboxEvents 스케줄러가 뒤늦게 보정한다.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            redisMatchQueue.add(member.getIndustry(), situation, matchRequest.getUuid(), epochMilli);
                        } catch (Exception e) {
                            // 실패는 드문 경로이므로 여기서만 REQUIRES_NEW로 즉시 FAIL 마킹한다.
                            applicationContext.getBean(MatchRequestService.class).markOutboxFailed(outbox.getId());
                            log.error("[MatchRequestService] Redis 대기열 적재 실패 - requestId: {}", matchRequest.getId(), e);
                            return;
                        }
                        try {
                            retryProcessor.retryOne(matchRequest.getUuid(), member.getIndustry());
                        } catch (Exception e) {
                            log.error("[MatchRequestService] 1차 즉시 매칭 시도 중 오류 발생 - matchRequestId: {}", matchRequest.getId(), e);
                        }
                    }
                }
        );

        return matchRequest;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutboxFailed(Long outboxId) {
        matchingOutboxRepository.findById(outboxId).ifPresent(MatchingOutbox::markFailed);
    }

    // NOT_SUPPORTED로 클래스 레벨 트랜잭션 상속을 차단 - 안 그러면 첫 조회로 커넥션을 잡은 채
    // 아래 tryLock() 대기(최대 5초)에 들어가버린다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void tryMatch(UUID matchRequestId, Industry industry) {
        String lockKey = "match:lock:" + industry.name();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // leaseTime 미지정 -> Redisson watchdog이 붙어 배치 루프가 길어져도 락이 새지 않는다.
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                try {
                    applicationContext.getBean(MatchRequestService.class).processMatch(matchRequestId, industry);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("500-1", "분산 락 획득 중 인터럽트가 발생했습니다.");
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void tryMatch(UUID matchRequestId) {
        MatchRequest matchRequest = matchRequestRepository.findByUuidWithMember(matchRequestId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        if (matchRequest.getStatus() != MatchStatus.PENDING) {
            return;
        }
        tryMatch(matchRequestId, matchRequest.getMember().getIndustry());
    }

    // self-invocation은 프록시를 안 거쳐 위 NOT_SUPPORTED가 적용 안 되므로, 이 메서드도 동일하게 막아둔다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void tryMatch(MatchRequest matchRequestParam) {
        tryMatch(matchRequestParam.getUuid(), matchRequestParam.getMember().getIndustry());
    }

    // 분산 락을 쥔 상태에서 대기자를 연속으로 이어 매칭하는 배치 루프. 최초 트리거된 요청을 먼저
    // 처리하고, 이후로는 매번 industry 전체에서 가장 오래 기다린 PENDING 요청을 다시 골라 이어간다.
    // 이 메서드 자체는 NOT_SUPPORTED - 실제 DB 쓰기는 connectPair()/connectWithBot()의 개별
    // REQUIRES_NEW에 위임해서, 한 쌍 커밋이 다른 쌍에 영향을 주지 않게 격리한다. Redis 제거는
    // 그 커밋이 성공한 뒤에만 best-effort로 수행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processMatch(UUID matchRequestId, Industry industry) {
        UUID currentTargetId = matchRequestId;
        int iterations = 0;

        while (currentTargetId != null && iterations++ < BATCH_MAX_ITERATIONS) {
            Optional<MatchRequest> currentOpt = matchRequestRepository.findByUuidWithMember(currentTargetId);
            if (currentOpt.isEmpty() || currentOpt.get().getStatus() != MatchStatus.PENDING) {
                break;
            }

            MatchRequest currentRequest = currentOpt.get();
            Situation situation = currentRequest.getSituation();
            long elapsedSeconds = Duration.between(currentRequest.getRequestedAt(), LocalDateTime.now()).getSeconds();

            Optional<MatchRequest> opponentOpt;
            try {
                opponentOpt = findOpponent(currentRequest, elapsedSeconds);
            } catch (Exception e) {
                log.error("[MatchRequestService] 배치 매칭 루프 중 후보 탐색 오류 발생 - matchRequestId: {}", currentTargetId, e);
                break;
            }

            if (opponentOpt.isPresent()) {
                MatchRequest opponent = opponentOpt.get();
                UUID opponentId = opponent.getUuid();
                Situation opponentSituation = opponent.getSituation();
                try {
                    applicationContext.getBean(MatchRequestService.class).connectPair(currentRequest.getUuid(), opponentId);
                } catch (Exception e) {
                    log.error("[MatchRequestService] 매칭 확정 실패 - currentId: {}, opponentId: {}", currentRequest.getUuid(), opponentId, e);
                    break;
                }
                safeRemoveFromQueue(industry, situation, currentRequest.getUuid());
                safeRemoveFromQueue(industry, opponentSituation, opponentId);
            } else if (elapsedSeconds >= BOT_FALLBACK_THRESHOLD_SECONDS) {
                try {
                    applicationContext.getBean(MatchRequestService.class).connectWithBot(currentRequest.getUuid());
                } catch (Exception e) {
                    log.error("[MatchRequestService] 봇 매칭 확정 실패 - matchRequestId: {}", currentTargetId, e);
                    break;
                }
                safeRemoveFromQueue(industry, situation, currentRequest.getUuid());
            } else {
                break;
            }

            currentTargetId = findOldestInSituations(industry, Arrays.asList(Situation.values()), null)
                    .map(MatchRequest::getUuid)
                    .orElse(null);
        }
    }

    // 매칭 확정 한 쌍만의 최소 단위 트랜잭션 - 이후 회차 실패가 이미 커밋된 쌍을 함께 롤백시키지 않도록 격리한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void connectPair(UUID currentId, UUID opponentId) {
        MatchRequest current = matchRequestRepository.findByUuidWithMember(currentId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        MatchRequest opponent = matchRequestRepository.findByUuidWithMember(opponentId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        if (current.getStatus() != MatchStatus.PENDING || opponent.getStatus() != MatchStatus.PENDING) {
            throw new ServiceException("409-1", "이미 처리된 매칭 요청입니다.");
        }
        connect(current, opponent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void connectWithBot(UUID currentId) {
        MatchRequest current = matchRequestRepository.findByUuidWithMember(currentId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        if (current.getStatus() != MatchStatus.PENDING) {
            throw new ServiceException("409-1", "이미 처리된 매칭 요청입니다.");
        }
        matchWithBot(current);
    }

    // ZREM 실패는 치명적이지 않다 - findOldestInSituations()의 자가 치유 로직이 다음 스캔에서 정리한다.
    private void safeRemoveFromQueue(Industry industry, Situation situation, UUID id) {
        try {
            redisMatchQueue.remove(industry, situation, id);
        } catch (Exception e) {
            log.warn("[MatchRequestService] Redis 대기열 정리 실패(자가 치유 로직이 추후 정리) - id: {}", id, e);
        }
    }

    // 상황 조건(Tier 1~3)에 맞는 대기열들을 찾아 가장 대기 시간이 오래된 상대를 탐색합니다.
    private Optional<MatchRequest> findOpponent(MatchRequest request, long elapsedSeconds) {
        Industry industry = request.getMember().getIndustry();
        Situation situation = request.getSituation();
        UUID excludeId = request.getUuid();
        if (elapsedSeconds < TIER1_THRESHOLD_SECONDS) {
            return findOldestInSituations(industry, List.of(situation), excludeId);
        }

        if (elapsedSeconds < TIER2_THRESHOLD_SECONDS) {
            Set<Situation> similarGroup = SituationSimilarity.getSimilarGroup(situation);
            return findOldestInSituations(industry, similarGroup, excludeId);
        }

        return findOldestInSituations(industry, Arrays.asList(Situation.values()), excludeId);
    }

    // 후보 UUID가 어느 situation 큐에서 나왔는지 함께 들고 다니기 위한 레코드 (자가 치유 ZREM에 필요).
    private record CandidateRef(Situation situation, UUID id) {}

    /**
     * 지정된 상황들의 Redis ZSet 상위 {@value #CANDIDATE_PAGE_SIZE}명 중 가장 오래 기다린 PENDING
     * 사용자 1명을 선출합니다. 후보 UUID를 먼저 모두 모아 IN절로 일괄 조회하고(N+1 방지), 스테일
     * 항목은 파이프라인 밖에서 별도로 ZREM 정리합니다(자가 치유).
     */
    private Optional<MatchRequest> findOldestInSituations(Industry industry, java.util.Collection<Situation> situations, UUID excludeId) {
        List<CandidateRef> candidateRefs = situations.stream()
                .flatMap(s -> redisMatchQueue.getOldestCandidates(industry, s, CANDIDATE_PAGE_SIZE).stream()
                        .filter(id -> !id.equals(excludeId))
                        .map(id -> new CandidateRef(s, id)))
                .toList();

        if (candidateRefs.isEmpty()) {
            return Optional.empty();
        }

        List<UUID> candidateIds = candidateRefs.stream().map(CandidateRef::id).toList();
        Map<UUID, MatchRequest> byId = matchRequestRepository.findAllByUuidIn(candidateIds).stream()
                .collect(java.util.stream.Collectors.toMap(MatchRequest::getUuid, mr -> mr));

        for (CandidateRef ref : candidateRefs) {
            MatchRequest mr = byId.get(ref.id());
            if (mr == null || mr.getStatus() != MatchStatus.PENDING) {
                redisMatchQueue.remove(industry, ref.situation(), ref.id());
            }
        }

        // requestedAt 동점 시 id를 2차 기준으로 더해 항상 같은 후보를 확정적으로 고른다 (낙관적 락 충돌 방지).
        return byId.values().stream()
                .filter(mr -> mr.getStatus() == MatchStatus.PENDING)
                .min(Comparator.comparing(MatchRequest::getRequestedAt).thenComparing(MatchRequest::getId));
    }

    public MatchRequest findById(UUID id) {
        return matchRequestRepository.findByUuid(id)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
    }

    @Transactional
    public void cancel(MatchRequest matchRequest, Member actor) {
        if (!matchRequest.getMember().getId().equals(actor.getId())) {
            throw new ServiceException("403-1", "접근 권한이 없습니다.");
        }

        // deleteByIdAndStatus가 clearAutomatically=true라 실행 후 matchRequest가 detach되어
        // lazy 필드(member)에 다시 접근할 수 없다 - 필요한 값은 미리 꺼내둔다.
        Industry industry = matchRequest.getMember().getIndustry();
        Situation situation = matchRequest.getSituation();
        UUID uuid = matchRequest.getUuid();

        // status를 먼저 SELECT해서 앱 코드에서 확인하면, 그 사이 매칭 배치(processMatch)가
        // 이 요청을 PENDING으로 읽어 확정시켜버려도 걸러내지 못한다(이 체크가 이미 지난
        // 스냅샷 기준이라서). DELETE 문 자체에 status 조건을 걸어 DB가 최신 커밋 상태
        // 기준으로 원자적으로 처리하게 한다.
        int deleted = matchRequestRepository.deleteByIdAndStatus(matchRequest.getId(), MatchStatus.PENDING);
        if (deleted == 0) {
            throw new ServiceException("409-1", "이미 매칭된 요청은 취소할 수 없습니다.");
        }

        redisMatchQueue.remove(industry, situation, uuid);
    }

    @Transactional
    public void cancelExpiredRequests() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(5);
        List<MatchRequest> expired = matchRequestRepository.findExpiredPending(MatchStatus.PENDING, expiredBefore);

        for (MatchRequest request : expired) {
            redisMatchQueue.remove(
                    request.getMember().getIndustry(),
                    request.getSituation(),
                    request.getUuid()
            );
        }

        matchRequestRepository.deleteAll(expired);
    }

    public List<MatchHistoryDto> findMatchHistoryByMember(Member member) {
        List<MatchRequest> requests = matchRequestRepository.findByMemberAndRoomStatus(member, ChatRoomStatus.CLOSED);

        List<Long> roomIds = requests.stream()
                .map(r -> r.getRoom().getId())
                .distinct()
                .toList();

        Map<Long, Boolean> botMap = chatRoomService.hasBotParticipantMap(roomIds);

        return requests.stream()
                .map(r -> new MatchHistoryDto(r, botMap.getOrDefault(r.getRoom().getId(), false)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SituationStatisticsDto> getSituationStatistics() {
        return matchRequestRepository.countActiveBySituation(MatchStatus.MATCHED, ChatRoomStatus.ACTIVE);
    }

    public boolean hasPendingRequest(Member member) {
        return matchRequestRepository.existsByMemberAndStatus(member, MatchStatus.PENDING);
    }

    public Situation findOpponentSituation(Long roomId, Long memberId) {
        return matchRequestRepository.findByRoomIdAndMemberIdNot(roomId, memberId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(MatchRequest::getSituation)
                .orElse(null);
    }

    @Transactional
    public void deleteAllByMember(Member member) {
        matchRequestRepository.deleteByMember(member);
    }
}
