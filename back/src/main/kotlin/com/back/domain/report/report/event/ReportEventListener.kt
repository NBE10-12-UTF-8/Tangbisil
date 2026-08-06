package com.back.domain.report.report.event

import com.back.domain.chat.chatRoomMessage.service.ChatMessageService
import com.back.domain.report.report.entity.ReportedMessage
import com.back.domain.report.report.repository.ReportRepository
import com.back.domain.report.report.repository.ReportedMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ReportEventListener(
    private val chatMessageService: ChatMessageService,
    private val reportedMessageRepository: ReportedMessageRepository,
    private val reportRepository: ReportRepository
) {
    private val log = LoggerFactory.getLogger(ReportEventListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleReportCreatedEvent(event: ReportCreatedEvent) {
        log.info("[ReportEventListener] 비동기 대화 백업 시작 - Thread: {}", Thread.currentThread().name)

        try {
            // 신규 트랜잭션에서 부모 Report 엔티티를 완전히 새로 조회 (준영속 롤백 및 지연로딩 에러 방지)
            val report = reportRepository.findById(event.reportId)
                .orElseThrow { IllegalArgumentException("신고 정보를 찾을 수 없습니다. ID: ${event.reportId}") }

            // 신고 유발 메시지 기점 이전 30개의 대화만 핀포인트로 조회해 오도록 연동
            val roomMessages = chatMessageService.getMessagesBeforeTarget(event.roomId, event.targetMessageId)

            val reportedMessages = roomMessages.map { msg ->
                ReportedMessage(
                    report,
                    msg.participant.member.uuid,
                    msg.participant.nickname,
                    msg.content,
                    msg.createdAt!!,
                    msg.uuid == event.targetMessageId
                )
            }
            reportedMessageRepository.saveAll(reportedMessages)

            log.info("[ReportEventListener] 비동기 대화 백업 완료 - Thread: {}", Thread.currentThread().name)
        } catch (e: Exception) {
            log.error("[ReportEventListener] 대화 백업 실패 - reportId: {}", event.reportId, e)
        }
    }
}
