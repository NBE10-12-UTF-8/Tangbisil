package com.back.domain.match.matchRequest.listener;

import com.back.domain.match.matchRequest.event.MatchSuccessEvent;
import com.back.domain.notification.service.MatchNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationListener.class);
    private final MatchNotificationService matchNotificationService;

    public MatchNotificationListener(MatchNotificationService matchNotificationService) {
        this.matchNotificationService = matchNotificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMatchSuccess(MatchSuccessEvent event) {
        log.info("[MatchNotificationListener] 매칭 성공 알림 비동기 적재 시작 - roomId: {}, requesterId: {}, opponentId: {}",
                event.roomId(), event.requesterId(), event.opponentId());

        try {
            matchNotificationService.notifyMatchSuccess(event.requesterId(), event.roomId());
            matchNotificationService.notifyMatchSuccess(event.opponentId(), event.roomId());
            log.info("[MatchNotificationListener] 매칭 성공 알림 비동기 적재 완료 - roomId: {}", event.roomId());
        } catch (Exception e) {
            log.error("[MatchNotificationListener] 알림 비동기 적재 중 예외 발생 - roomId: {}", event.roomId(), e);
        }
    }
}