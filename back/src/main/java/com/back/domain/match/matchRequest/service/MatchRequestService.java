package com.back.domain.match.matchRequest.service;

import com.back.domain.bot.BotAccounts;
import com.back.domain.bot.BotReplyTriggerEvent;
import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.service.ChatRoomService;
import com.back.domain.match.matchRequest.dto.MatchHistoryDto;
import com.back.domain.match.matchRequest.dto.SituationStatisticsDto;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.entity.SituationSimilarity;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.notification.service.MatchNotificationService;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final long TIER1_THRESHOLD_SECONDS = 15; // 15초 후 유사 상황 매칭
    private static final long TIER2_THRESHOLD_SECONDS = 30; // 30초 후 산업군 전체 매칭
    // 30초(Tier2)까지도 실제 유저를 못 찾으면, 이 시점부터 봇으로 폴백한다.
    // Tier2보다 늦게 잡아서 "실제 사람끼리 매칭될 기회"를 최대한 먼저 준다.
    private static final long BOT_FALLBACK_THRESHOLD_SECONDS = 35;

    private List<MatchRequest> findCandidates(Industry industry, Situation situation, long elapsedSeconds) {
        if (elapsedSeconds < TIER1_THRESHOLD_SECONDS) {
            return matchRequestRepository.findPendingByIndustryAndSituation(industry, situation, MatchStatus.PENDING);
        }
        if (elapsedSeconds < TIER2_THRESHOLD_SECONDS) {
            Set<Situation> similarGroup = SituationSimilarity.getSimilarGroup(situation);
            return matchRequestRepository.findPendingByIndustryAndSituations(industry, similarGroup, MatchStatus.PENDING);
        }
        return matchRequestRepository.findPendingByIndustry(industry, MatchStatus.PENDING);
    }

    private void connect(MatchRequest matchRequest, MatchRequest other) {
        int claimedSelf = matchRequestRepository.claimPending(matchRequest.getId());
        int claimedOther = matchRequestRepository.claimPending(other.getId());

        if (claimedSelf == 0 || claimedOther == 0) {
            // 둘 다 성공해야 유효한 매칭이다. 한쪽만 성공했다면 "매칭됨인데 방은 없는"
            // 상태로 영구히 남지 않게, 성공한 쪽을 다시 PENDING으로 되돌린다.
            // 단, 봇 전용 임시 요청이 선점됐다 실패한 경우엔 PENDING으로 되돌리지 않고 삭제한다 -
            // 그대로 두면 대기열에 봇의 MatchRequest가 노출되어 실제 유저와 오작동 매칭될 수 있다.
            revertOrDiscard(matchRequest, claimedSelf);
            revertOrDiscard(other, claimedOther);

            log.warn("[MatchRequestService] 동시 매칭 충돌 감지, 이번 시도는 취소 - a={}, b={}",
                    matchRequest.getId(), other.getId());
            return;
        }

        ChatRoom chatRoom = chatRoomService.createChatRoom(List.of(matchRequest.getMember(), other.getMember()));
        matchRequestRepository.assignRoom(matchRequest.getId(), chatRoom.getId());
        matchRequestRepository.assignRoom(other.getId(), chatRoom.getId());

        // claimPending/assignRoom은 벌크 UPDATE라 영속성 컨텍스트를 안 거친다.
        // retryPendingMatches()의 반복문이 같은 엔티티 인스턴스를 재사용하며 상태를 확인하므로,
        // 자바 객체 필드도 반드시 맞춰줘야 한다 (안 그러면 이미 매칭된 요청을 또 매칭 시도함).
        // (clearAutomatically=true라 이 시점 matchRequest/other는 영속성 컨텍스트에서 detached 상태.
        //  matchWith()는 DB 반영용이 아니라 같은 트랜잭션 내 로컬 필드 동기화 목적일 뿐이다.)
        matchRequest.matchWith(chatRoom);
        other.matchWith(chatRoom);

        triggerBotReplyIfNeeded(matchRequest.getMember(), other.getMember(), chatRoom.getId());
        notifyMatchSuccessIfNeeded(matchRequest.getMember(), other.getMember(), chatRoom.getId());
    }

    // 봇은 알림을 받을 수 없으니 실제 유저 쪽에만 발행한다.
    private void notifyMatchSuccessIfNeeded(Member a, Member b, UUID roomId) {
        if (!BotAccounts.isBotEmail(a.getEmail())) {
            matchNotificationService.notifyMatchSuccess(a.getId(), roomId);
        }
        if (!BotAccounts.isBotEmail(b.getEmail())) {
            matchNotificationService.notifyMatchSuccess(b.getId(), roomId);
        }
    }

    // 선점 실패 시 처리: 봇 전용 임시 요청이면 대기열에 남기지 말고 삭제,
    // 실제 유저 요청이면 다음 기회를 위해 PENDING으로 되돌린다.
    private void revertOrDiscard(MatchRequest request, int claimed) {
        if (claimed != 1) {
            return; // 애초에 선점 안 됐으면 손댈 것 없음
        }
        if (BotAccounts.isBotEmail(request.getMember().getEmail())) {
            matchRequestRepository.delete(request);
        } else {
            matchRequestRepository.revertToPending(request.getId());
        }
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
        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, situation));
        tryMatch(matchRequest);
        return matchRequest;
    }

    @Transactional
    public void tryMatch(UUID matchRequestId) {
        MatchRequest matchRequest = matchRequestRepository.findById(matchRequestId)
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));
        tryMatch(matchRequest);
    }

    @Transactional
    public void tryMatch(MatchRequest matchRequestParam) {
        // 호출자가 넘긴 matchRequest가 다른 트랜잭션에서 만들어진 detached 엔티티일 수 있다
        // (예: 테스트에서 save 직후 바로 넘기는 경우). member 프록시가 세션 없이 초기화
        // 실패하는 걸 막기 위해, 이 트랜잭션 안에서 fetch join으로 다시 로딩한다.
        MatchRequest matchRequest = matchRequestRepository.findByIdWithMember(matchRequestParam.getId())
                .orElseThrow(() -> new ServiceException("404-1", "매칭 요청을 찾을 수 없습니다."));

        if (matchRequest.getStatus() == MatchStatus.MATCHED) {
            return;
        }
        if (BotAccounts.isBotEmail(matchRequest.getMember().getEmail())) {
            return;
        }

        Industry industry = matchRequest.getMember().getIndustry();
        Situation situation = matchRequest.getSituation();
        long elapsedSeconds = Duration.between(matchRequest.getRequestedAt(), LocalDateTime.now()).getSeconds();

        List<MatchRequest> candidates = findCandidates(industry, situation, elapsedSeconds);
        Optional<MatchRequest> opponent = candidates.stream()
                .filter(r -> !r.getId().equals(matchRequest.getId()))
                .filter(r -> !BotAccounts.isBotEmail(r.getMember().getEmail()))
                .findFirst();

        if (opponent.isPresent()) {
            connect(matchRequest, opponent.get());
            return;
        }

        if (elapsedSeconds >= BOT_FALLBACK_THRESHOLD_SECONDS) {
            matchWithBot(matchRequest);
        }
    }
    @Transactional
    public void retryPendingMatches() {
        List<UUID> pendingIds = matchRequestRepository.findAllByStatus(MatchStatus.PENDING).stream()
                .map(MatchRequest::getId)
                .toList();

        for (UUID id : pendingIds) {
            try {
                retryProcessor.retryOne(id);
            } catch (Exception e) {
                log.error("[MatchRequestService] 재시도 중 매칭 실패 - requestId: {}", id, e);
            }
        }
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
    }

    @Transactional
    public void cancelExpiredRequests() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(5);
        List<MatchRequest> expired = matchRequestRepository
                .findExpiredPending(MatchStatus.PENDING, expiredBefore);
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