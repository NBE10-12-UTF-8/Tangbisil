package com.back.domain.report.report.event;

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportedMessage;
import com.back.domain.report.report.repository.ReportRepository;
import com.back.domain.report.report.repository.ReportedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportEventListener {
    private final ChatMessageService chatMessageService;
    private final ReportedMessageRepository reportedMessageRepository;
    private final ReportRepository reportRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReportCreatedEvent(ReportCreatedEvent event) {
        log.info("[ReportEventListener] 비동기 대화 백업 시작 - Thread: {}", Thread.currentThread().getName());

        try {
            // 신규 트랜잭션에서 부모 Report 엔티티를 완전히 새로 조회 (준영속 롤백 및 지연로딩 에러 방지)
            Report report = reportRepository.findById(event.reportId())
                    .orElseThrow(() -> new IllegalArgumentException("신고 정보를 찾을 수 없습니다. ID: " + event.reportId()));

            // 신고 유발 메시지 기점 이전 30개의 대화만 핀포인트로 조회해 오도록 연동
            List<ChatMessage> roomMessages = chatMessageService.getMessagesBeforeTarget(event.roomId(), event.targetMessageId());

            List<ReportedMessage> reportedMessages = roomMessages.stream()
                    .map(msg -> new ReportedMessage(
                            report,
                            msg.getParticipant().getMember().getUuid(),
                            msg.getParticipant().getNickname(),
                            msg.getContent(),
                            msg.getCreatedAt(),
                            msg.getUuid().equals(event.targetMessageId())
                    ))
                    .toList();
            reportedMessageRepository.saveAll(reportedMessages);

            log.info("[ReportEventListener] 비동기 대화 백업 완료 - Thread: {}", Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[ReportEventListener] 대화 백업 실패 - reportId: {}", event.reportId(), e);
        }
    }
}