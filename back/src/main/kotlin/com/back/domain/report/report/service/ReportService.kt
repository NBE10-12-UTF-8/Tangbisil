package com.back.domain.report.report.service

import com.back.domain.chat.chatRoom.service.ChatRoomService
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService
import com.back.domain.member.member.entity.Member
import com.back.domain.report.report.dto.ReportAdmDetailDto
import com.back.domain.report.report.dto.ReportStatusUpdateDto
import com.back.domain.report.report.entity.Report
import com.back.domain.report.report.entity.ReportStatus
import com.back.domain.report.report.event.ReportCreatedEvent
import com.back.domain.report.report.repository.ReportRepository
import com.back.domain.report.report.repository.ReportedMessageRepository
import com.back.global.exception.ServiceException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ReportService(
    private val reportRepository: ReportRepository,
    private val chatRoomService: ChatRoomService,
    private val chatMessageService: ChatMessageService,
    private val chatRoomParticipantService: ChatRoomParticipantService,
    private val eventPublisher: ApplicationEventPublisher,
    private val reportedMessageRepository: ReportedMessageRepository
) {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    @Transactional
    fun createReport(reporter: Member, roomId: UUID, reportedMessageId: UUID, reason: String?): Report {
        log.info("[ReportService] 신고 접수 시작 - Thread: {}", Thread.currentThread().name)

        val room = chatRoomService.getChatRoom(roomId)
        val targetMessage = chatMessageService.getMessage(reportedMessageId)

        if (!chatRoomParticipantService.isParticipant(room.id!!, reporter.id!!)) {
            throw ServiceException("403-2", "채팅방 참여자만 신고할 수 있습니다.")
        }
        if (targetMessage.chatRoom.id != room.id) {
            throw ServiceException("400-3", "요청한 채팅방의 메시지가 아닙니다.")
        }

        val participant = targetMessage.participant
        val reported = participant.member

        if (reporter.id == reported.id) {
            throw ServiceException("400-1", "자신을 신고할 수 없습니다.")
        }
        if (reportRepository.existsByReporterAndReportedMessageId(reporter, reportedMessageId)) {
            throw ServiceException("400-2", "이미 신고된 메시지입니다.")
        }

        val report = reportRepository.save(Report(reporter, reported, room, reportedMessageId, reason))

        // reportId/roomId는 내부 PK, targetMessageId는 FK 없는 UUID라 원형 그대로 이벤트에 싣는다
        eventPublisher.publishEvent(ReportCreatedEvent(report.id!!, room.id!!, reportedMessageId))

        log.info("[ReportService] 신고 접수 완료 - Thread: {}", Thread.currentThread().name)
        return report
    }

    fun findAllWithMember(status: ReportStatus?, pageable: Pageable): Page<Report> {
        if (status == null) {
            return reportRepository.findAllWithMember(pageable)
        }
        return reportRepository.findAllWithMemberAndStatus(status, pageable)
    }

    fun getReportDetailForAdmin(reportId: UUID): ReportAdmDetailDto {
        val report = reportRepository.findWithMemberByUuid(reportId)
            ?: throw ServiceException("404-1", "존재하지 않는 신고서입니다.")

        val backupMessages = reportedMessageRepository.findByReportIdOrderBySentAtAsc(report.id!!)

        // senderMemberId(ReportedMessage)가 FK 없는 UUID 특수 필드라 여기서도 uuid로 비교한다
        val reporterId = report.reporter?.uuid
        val reportedId = report.reported?.uuid

        var participantSuffix = 'A'
        val participantMap = HashMap<UUID, String>()
        val messageDtos = ArrayList<ReportAdmDetailDto.ReportedMessageAdmDto>()

        for (msg in backupMessages) {
            val senderId = msg.senderMemberId

            val label = if (reporterId != null && reporterId == senderId) {
                "신고자"
            } else if (reportedId != null && reportedId == senderId) {
                "피신고자"
            } else {
                // 처음 등장하는 제3자에게만 알파벳 라벨을 새로 배정하고, 재등장 시 같은 라벨을 재사용한다
                participantMap.getOrPut(senderId) {
                    val newLabel = "참여자 $participantSuffix"
                    participantSuffix++
                    newLabel
                }
            }

            messageDtos.add(
                ReportAdmDetailDto.ReportedMessageAdmDto(
                    msg.senderNickname,
                    label,
                    msg.content,
                    msg.sentAt,
                    msg.isTarget
                )
            )
        }

        return ReportAdmDetailDto(report, messageDtos)
    }

    @Transactional
    fun toggleReportStatus(reportId: UUID): ReportStatusUpdateDto {
        val report = reportRepository.findByUuid(reportId)
            ?: throw ServiceException("404-1", "존재하지 않는 신고서입니다.")

        report.toggleStatus()

        return ReportStatusUpdateDto(report.uuid, report.status)
    }
}
