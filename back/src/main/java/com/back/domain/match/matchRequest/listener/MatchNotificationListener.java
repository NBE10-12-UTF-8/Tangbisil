package com.back.domain.match.matchRequest.listener;

import com.back.domain.bot.BotAccounts;
import com.back.domain.match.matchRequest.event.MatchSuccessEvent;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.notification.service.MatchNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.UUID;

@Component
public class MatchNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationListener.class);
    private final MatchNotificationService matchNotificationService;
    private final MemberRepository memberRepository;

    public MatchNotificationListener(MatchNotificationService matchNotificationService, MemberRepository memberRepository) {
        this.matchNotificationService = matchNotificationService;
        this.memberRepository = memberRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMatchSuccess(MatchSuccessEvent event) {
        log.info("[MatchNotificationListener] 매칭 성공 알림 비동기 적재 시작 - roomId: {}, requesterId: {}, opponentId: {}",
                event.roomId(), event.requesterId(), event.opponentId());

        try {
            // 요청자(나)에게 알림 발송
            sendNotificationIfNotBot(event.requesterId(), event.roomId());

            // 상대방에게 알림 발송
            sendNotificationIfNotBot(event.opponentId(), event.roomId());

            log.info("[MatchNotificationListener] 매칭 성공 알림 비동기 적재 완료 - roomId: {}", event.roomId());
        } catch (Exception e) {
            log.error("[MatchNotificationListener] 알림 비동기 적재 중 예외 발생 - roomId: {}", event.roomId(), e);
        }
    }

    //회원 ID를 받아 DB에서 조회 후, 봇 계정이 아닌 실제 사람일 때만 알림을 발송하는 헬퍼 메서드
    private void sendNotificationIfNotBot(Long memberId, UUID roomId) {
        memberRepository.findById(memberId).ifPresent(member -> {
            if (!BotAccounts.isBotEmail(member.getEmail())) {
                matchNotificationService.notifyMatchSuccess(member.getId(), roomId);
            }
        });
    }
}