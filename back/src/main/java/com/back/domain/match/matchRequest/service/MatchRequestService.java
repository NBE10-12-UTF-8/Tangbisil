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
    // 30초(Tier2)까지도 실제 유저를 못 찾으면, 이 시점부터 봇으로 폴백한다.
    // Tier2보다 늦게 잡아서 "실제 사람끼리 매칭될 기회"를 최대한 먼저 준다.
    private static final long BOT_FALLBACK_THRESHOLD_SECONDS = 35;

    private void connect(MatchRequest matchRequest, MatchRequest other) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(List.of(matchRequest.getMember(), other.getMember()));
        // matchRequest/other는 이 트랜잭션에서 로딩된 영속 상태이므로,
        // matchWith()로 필드를 바꾸면 커밋 시점에 더티 체킹으로 room/status가 함께 반영된다.
        // (예전엔 assignRoom 네이티브 UPDATE로 room만 먼저 반영했는데, 그 벌크 쿼리가
        // 영속성 컨텍스트를 비워버려서 뒤이은 matchWith()의 status 변경이 유실되는 버그가 있었다.)
        matchRequest.matchWith(chatRoom);
        other.matchWith(chatRoom);
        triggerBotReplyIfNeeded(matchRequest.getMember(), other.getMember(), chatRoom.getId());

        // RDB 최종 커밋 완료 시점에 비동기로 알림이 가도록 이벤트를 발행합니다.
        eventPublisher.publishEvent(new MatchSuccessEvent(chatRoom.getId(), matchRequest.getMember().getId(), other.getMember().getId()));
    }

    // 실제 유저 상대를 못 찾고 봇 폴백 기준 시간이 지난 요청을, 그 시점에 즉석으로 만든
    // 봇 요청과 매칭시켜준다. 봇은 평소엔 대기열에 없다 - 실제 유저끼리 매칭될 기회를 먼저 준다.
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

    private void triggerBotReplyIfNeeded(Member requester, Member other, UUID roomId) {
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

        // RDB에 대기 정보 저장
        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, situation));

        // RDB 동일 트랜잭션 내에 아웃박스 이벤트 저장
        long epochMilli = matchRequest.getRequestedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        MatchingOutbox outbox = MatchingOutbox.create(
                matchRequest.getId(),
                member.getIndustry(),
                situation,
                epochMilli
        );
        matchingOutboxRepository.save(outbox);

        // AFTER_COMMIT 리스너 등록 (커밋 완료 직후 Redis ZADD 기동)
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        boolean isLoaded = false;
                        try {
                            // Redis ZSet 대기열 적재
                            redisMatchQueue.add(
                                    member.getIndustry(),
                                    situation,
                                    matchRequest.getId(),
                                    epochMilli
                            );
                            // 적재 성공 시 아웃박스 상태 SUCCESS 마킹
                            // 주의: AFTER_COMMIT 시점엔 활성 트랜잭션이 없어서(원본 트랜잭션은 이미 끝남)
                            // 일반 save()/saveAndFlush() 호출로는 반영이 보장되지 않는다(TransactionRequiredException).
                            // processMatch()와 동일하게, 프록시를 거쳐 REQUIRES_NEW 트랜잭션으로 실행해야
                            // 확실히 커밋된다.
                            applicationContext.getBean(MatchRequestService.class).markOutboxSuccess(outbox.getId());
                            isLoaded = true;
                        } catch (Exception e) {
                            // 적재 실패 시 FAIL 마킹 (마찬가지로 REQUIRES_NEW로 확실히 반영)
                            applicationContext.getBean(MatchRequestService.class).markOutboxFailed(outbox.getId());
                        }

                        if (isLoaded) {
                            try {
                                // ZADD 적재 성공 직후, 별도 스케줄러 대기 없이 비동기로 즉시 1차 매칭 시도
                                retryProcessor.retryOne(matchRequest.getId());
                            } catch (Exception e) {
                                log.error("[MatchRequestService] 1차 즉시 매칭 시도 중 오류 발생 - matchRequestId: {}", matchRequest.getId(), e);
                            }
                        }
                    }
                }
        );

        return matchRequest;
    }

    // AFTER_COMMIT 콜백(활성 트랜잭션이 없는 시점)에서 호출되므로, 독립적인 REQUIRES_NEW 트랜잭션으로
    // 실행해 확실히 커밋되도록 한다. 반드시 프록시(applicationContext.getBean)를 거쳐 호출해야 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutboxSuccess(UUID outboxId) {
        matchingOutboxRepository.findById(outboxId).ifPresent(MatchingOutbox::markSuccess);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutboxFailed(UUID outboxId) {
        matchingOutboxRepository.findById(outboxId).ifPresent(MatchingOutbox::markFailed);
    }

    public void tryMatch(UUID matchRequestId) {
        MatchRequest matchRequest = matchRequestRepository.findByIdWithMember(matchRequestId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        if (matchRequest.getStatus() != MatchStatus.PENDING) {
            return; // 이미 매칭되었거나 취소된 유저면 조용히 탈출 (Early Exit)
        }
        Industry industry = matchRequest.getMember().getIndustry();
        String lockKey = "match:lock:" + industry.name();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 락 획득 시도 (대기 최대 5초, 락 소유 최대 10초)
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 프록시를 거쳐 REQUIRES_NEW 트랜잭션으로 실행 - 이 호출이 반환된 시점에는
                    // DB 반영이 이미 100% 커밋 완료된 상태이므로, 그 뒤에 오는 unlock()이
                    // 항상 "커밋 이후"에만 일어남을 보장한다.
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

    public void tryMatch(MatchRequest matchRequestParam) {
        tryMatch(matchRequestParam.getId());
    }

    // 분산 락을 쥔 상태에서만 호출되는 실제 매칭 처리 트랜잭션.
    // REQUIRES_NEW로 독립 커밋시켜, tryMatch()의 락 해제가 이 메서드의 커밋 이후에만 일어나도록 강제한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processMatch(UUID matchRequestId, Industry industry) {
        // [중요] 락에 들어온 순간, DB의 최신 상태를 단건 인덱스로 초고속 검증합니다.
        MatchRequest currentRequest = matchRequestRepository.findByIdWithMember(matchRequestId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        if (currentRequest.getStatus() != MatchStatus.PENDING) {
            return; // 대기 도중 취소되었거나 이미 다른 사람과 매칭이 끝난 상태면 스킵 (Early Exit)
        }
        Situation situation = currentRequest.getSituation();
        long elapsedSeconds = Duration.between(currentRequest.getRequestedAt(), LocalDateTime.now()).getSeconds();
        Optional<MatchRequest> opponentOpt = findOpponent(currentRequest, elapsedSeconds);
        if (opponentOpt.isPresent()) {
            MatchRequest opponent = opponentOpt.get();

            // RDB 매칭 성공 처리 및 방 생성 진행
            connect(currentRequest, opponent);
            // Redis ZSet 대기열에서 나와 상대방을 즉시 원자적으로 선점 제거 (ZREM)
            redisMatchQueue.remove(industry, situation, currentRequest.getId());
            redisMatchQueue.remove(industry, opponent.getSituation(), opponent.getId());
            return;
        }
        // 봇 매칭 폴백
        if (elapsedSeconds >= BOT_FALLBACK_THRESHOLD_SECONDS) {
            matchWithBot(currentRequest);
            // ZSet 대기열에서 나 자신을 제거하고 봇 매칭 진행
            redisMatchQueue.remove(industry, situation, currentRequest.getId());
        }
    }

    // 상황 조건(Tier 1~3)에 맞는 대기열들을 찾아 가장 대기 시간이 오래된 상대를 탐색합니다.
    private Optional<MatchRequest> findOpponent(MatchRequest request, long elapsedSeconds) {
        Industry industry = request.getMember().getIndustry();
        Situation situation = request.getSituation();
        UUID excludeId = request.getId();
        // Tier 1: 동일 상황 ZSet에서 가장 오래 기다린 사람
        if (elapsedSeconds < TIER1_THRESHOLD_SECONDS) {
            return findOldestInSituations(industry, List.of(situation), excludeId);
        }

        // Tier 2: 유사 상황 ZSet 그룹들에서 가장 오래 기다린 사람
        if (elapsedSeconds < TIER2_THRESHOLD_SECONDS) {
            Set<Situation> similarGroup = SituationSimilarity.getSimilarGroup(situation);
            return findOldestInSituations(industry, similarGroup, excludeId);
        }

        // Tier 3: 업종 내 모든 상황 ZSet 중에서 가장 오래 기다린 사람
        return findOldestInSituations(industry, Arrays.asList(Situation.values()), excludeId);
    }

    /**
     * 지정된 여러 상황(Situations)의 Redis ZSet 키들을 전수 검사하여,
     * 각 대기열 1등들 중 가중치(Score)가 가장 오래된 사용자 1명을 최종 선출합니다.
     * 본인(excludeId)은 후보에서 제외하여 셀프 매칭을 방지합니다.
     */
    private Optional<MatchRequest> findOldestInSituations(Industry industry, java.util.Collection<Situation> situations, UUID excludeId) {
        return situations.stream()
                .flatMap(s -> redisMatchQueue.getOldestTwo(industry, s).stream())
                .filter(id -> !id.equals(excludeId))
                .flatMap(id -> matchRequestRepository.findByIdWithMember(id).stream())
                .filter(mr -> mr.getStatus() == MatchStatus.PENDING)
                .min(Comparator.comparing(MatchRequest::getRequestedAt));
    }

    public MatchRequest findById(UUID id) {
        return matchRequestRepository.findById(id)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
    }

    @Transactional
    public void cancel(MatchRequest matchRequest, Member actor) {
        if (!matchRequest.getMember().getId().equals(actor.getId())) {
            throw new ServiceException("403-1", "접근 권한이 없습니다.");
        }
        if (matchRequest.getStatus() == MatchStatus.MATCHED) {
            throw new ServiceException("409-1", "이미 매칭된 요청은 취소할 수 없습니다.");
        }

        matchRequestRepository.delete(matchRequest);

        // Redis ZSet 대기열에서도 본인을 즉시 안전하게 제거
        redisMatchQueue.remove(
                matchRequest.getMember().getIndustry(),
                matchRequest.getSituation(),
                matchRequest.getId()
        );
    }

    @Transactional
    public void cancelExpiredRequests() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(5);
        List<MatchRequest> expired = matchRequestRepository.findExpiredPending(MatchStatus.PENDING, expiredBefore);

        // DB에서 지우기 전에, 각 만료 건을 Redis ZSet 대기열에서 제거
        for (MatchRequest request : expired) {
            redisMatchQueue.remove(
                    request.getMember().getIndustry(),
                    request.getSituation(),
                    request.getId()
            );
        }

        matchRequestRepository.deleteAll(expired);
    }

    public List<MatchHistoryDto> findMatchHistoryByMember(Member member) {
        List<MatchRequest> requests = matchRequestRepository.findByMemberAndRoomStatus(member, ChatRoomStatus.CLOSED);

        List<UUID> roomIds = requests.stream()
                .map(r -> r.getRoom().getId())
                .distinct()
                .toList();

        Map<UUID, Boolean> botMap = chatRoomService.hasBotParticipantMap(roomIds);

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

    // 채팅방 입장 시 상대방이 선택한 상황을 노출하기 위한 조회
    public Situation findOpponentSituation(UUID roomId, UUID memberId) {
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