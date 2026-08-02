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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
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

    private static final long TIER1_THRESHOLD_SECONDS = 15; // 15초 후 유사 상황 매칭
    private static final long TIER2_THRESHOLD_SECONDS = 30; // 30초 후 산업군 전체 매칭
    // 30초(Tier2)까지도 실제 유저를 못 찾으면, 이 시점부터 봇으로 폴백한다.
    // Tier2보다 늦게 잡아서 "실제 사람끼리 매칭될 기회"를 최대한 먼저 준다.
    private static final long BOT_FALLBACK_THRESHOLD_SECONDS = 35;

    private void connect(MatchRequest matchRequest, MatchRequest other) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(List.of(matchRequest.getMember(), other.getMember()));
        matchRequestRepository.assignRoom(matchRequest.getId(), chatRoom.getId());
        matchRequestRepository.assignRoom(other.getId(), chatRoom.getId());
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
                        try {
                            // Redis ZSet 대기열 적재
                            redisMatchQueue.add(
                                    member.getIndustry(),
                                    situation,
                                    matchRequest.getId(),
                                    epochMilli
                            );
                            // 적재 성공 시 아웃박스 상태 SUCCESS 마킹
                            outbox.markSuccess();
                            matchingOutboxRepository.save(outbox);
                        } catch (Exception e) {
                            // 적재 실패 시 FAIL 마킹
                            outbox.markFailed();
                            matchingOutboxRepository.save(outbox);
                        }
                    }
                }
        );

        return matchRequest;
    }

    @Transactional
    public void tryMatch(UUID matchRequestId) {
        MatchRequest matchRequest = matchRequestRepository.findByIdWithMember(matchRequestId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        if (matchRequest.getStatus() != MatchStatus.PENDING) {
            return; // 이미 매칭되었거나 취소된 유저면 조용히 탈출 (Early Exit)
        }
        Industry industry = matchRequest.getMember().getIndustry();
        Situation situation = matchRequest.getSituation();
        String lockKey = "match:lock:" + industry.name();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 락 획득 시도 (대기 최대 5초, 락 소유 최대 10초)
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // [중요] 락에 들어온 순간, DB의 최신 상태를 단건 인덱스로 초고속 검증합니다.
                    MatchRequest currentRequest = matchRequestRepository.findByIdWithMember(matchRequestId)
                            .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
                    if (currentRequest.getStatus() != MatchStatus.PENDING) {
                        return; // 대기 도중 취소되었거나 이미 다른 사람과 매칭이 끝난 상태면 스킵 (Early Exit)
                    }
                    long elapsedSeconds = Duration.between(currentRequest.getRequestedAt(), LocalDateTime.now()).getSeconds();
                    Optional<MatchRequest> opponentOpt = findOpponent(currentRequest, elapsedSeconds);
                    if (opponentOpt.isPresent()) {
                        MatchRequest opponent = opponentOpt.get();

                        // Redis ZSet 대기열에서 나와 상대방을 즉시 원자적으로 선점 제거 (ZREM)
                        redisMatchQueue.remove(industry, situation, currentRequest.getId());
                        redisMatchQueue.remove(industry, opponent.getSituation(), opponent.getId());
                        // RDB 매칭 성공 처리 및 방 생성 진행
                        connect(currentRequest, opponent);
                        return;
                    }
                    // 봇 매칭 폴백
                    if (elapsedSeconds >= BOT_FALLBACK_THRESHOLD_SECONDS) {
                        // ZSet 대기열에서 나 자신을 제거하고 봇 매칭 진행
                        redisMatchQueue.remove(industry, situation, currentRequest.getId());
                        matchWithBot(currentRequest);
                    }
                } finally {
                    lock.unlock(); // 락 반납
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("500-1", "분산 락 획득 중 인터럽트가 발생했습니다.");
        }
    }

    @Transactional
    public void tryMatch(MatchRequest matchRequestParam) {
        tryMatch(matchRequestParam.getId());
    }

    // 상황 조건(Tier 1~3)에 맞는 대기열들을 찾아 가장 대기 시간이 오래된 상대를 탐색합니다.
    private Optional<MatchRequest> findOpponent(MatchRequest request, long elapsedSeconds) {
        Industry industry = request.getMember().getIndustry();
        Situation situation = request.getSituation();
        // Tier 1: 동일 상황 ZSet에서 가장 오래 기다린 사람
        if (elapsedSeconds < TIER1_THRESHOLD_SECONDS) {
            return findOldestInSituations(industry, List.of(situation));
        }

        // Tier 2: 유사 상황 ZSet 그룹들에서 가장 오래 기다린 사람
        if (elapsedSeconds < TIER2_THRESHOLD_SECONDS) {
            Set<Situation> similarGroup = SituationSimilarity.getSimilarGroup(situation);
            return findOldestInSituations(industry, similarGroup);
        }

        // Tier 3: 업종 내 모든 상황 ZSet 중에서 가장 오래 기다린 사람
        return findOldestInSituations(industry, Arrays.asList(Situation.values()));
    }

    /**
     * 지정된 여러 상황(Situations)의 Redis ZSet 키들을 전수 검사하여,
     * 각 대기열 1등들 중 가중치(Score)가 가장 오래된 사용자 1명을 최종 선출합니다.
     */
    private Optional<MatchRequest> findOldestInSituations(Industry industry, java.util.Collection<Situation> situations) {
        return situations.stream()
                .map(s -> redisMatchQueue.getOldest(industry, s))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(id -> matchRequestRepository.findByIdWithMember(id).stream())
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