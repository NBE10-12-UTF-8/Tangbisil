package com.back.domain.report.report.service;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.service.ChatRoomService;
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.service.ChatRoomParticipantService;
import com.back.domain.member.member.entity.Member;
import com.back.domain.report.report.dto.ReportAdmDetailDto;
import com.back.domain.report.report.dto.ReportStatusUpdateDto;
import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportStatus;
import com.back.domain.report.report.entity.ReportedMessage;
import com.back.domain.report.report.event.ReportCreatedEvent;
import com.back.domain.report.report.repository.ReportRepository;
import com.back.domain.report.report.repository.ReportedMessageRepository;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportedMessageRepository reportedMessageRepository;

    @Transactional
    public Report createReport(Member reporter, UUID roomId, UUID reportedMessageId, String reason) {
        log.info("[ReportService] 신고 접수 시작 - Thread: {}", Thread.currentThread().getName());

        // 1. ChatRoomService를 통해 채팅방 검증 및 조회
        ChatRoom room = chatRoomService.getChatRoom(roomId);

        // 2. ChatMessageService를 통해 메시지 검증 및 조회
        ChatMessage targetMessage = chatMessageService.getMessage(reportedMessageId);

        // 3. 신고자가 해당 채팅방의 참여자(Participant)인지 서비스 위임 검증
        if (!chatRoomParticipantService.isParticipant(room.getId(), reporter.getId())) {
            throw new ServiceException("403-2", "채팅방 참여자만 신고할 수 있습니다.");
        }

        // 4. 신고 대상 메시지가 실제 요청한 채팅방(roomId)의 메시지가 맞는지 소유권 검증
        if (!targetMessage.getChatRoom().getId().equals(room.getId())) {
            throw new ServiceException("400-3", "요청한 채팅방의 메시지가 아닙니다.");
        }

        // 5. 메시지 발송인(피신고자) 특정
        ChatRoomParticipant participant = targetMessage.getParticipant();
        Member reported = participant.getMember();

        // 6. 자기 자신 신고 금지 차단
        if (reporter.getId().equals(reported.getId())) {
            throw new ServiceException("400-1", "자신을 신고할 수 없습니다.");
        }

        // 7. 동일 메시지 중복 신고 차단
        if (reportRepository.existsByReporterAndReportedMessageId(reporter, reportedMessageId)) {
            throw new ServiceException("400-2", "이미 신고된 메시지입니다.");
        }

        // 8. 신고(Report) 객체 생성 및 저장
        Report report = reportRepository.save(new Report(reporter, reported, room, reportedMessageId, reason));

        // 9. 비동기 이벤트 발행 (엔티티 대신 식별자 ID만 전달하도록 수정)
        eventPublisher.publishEvent(new ReportCreatedEvent(report.getId(), room.getId(), reportedMessageId));
        // reportId/roomId는 순수 내부 PK 전달(이벤트는 클라이언트에 노출되지 않음), targetMessageId만 FK 없는 특수 UUID 필드 원형 유지

        log.info("[ReportService] 신고 접수 완료 - Thread: {}", Thread.currentThread().getName());
        return report;
    }

    // 관리자용 신고 목록 페이징 조회
    public Page<Report> findAllWithMember(ReportStatus status, Pageable pageable) {
        if (status == null) {
            return reportRepository.findAllWithMember(pageable);
        }
        return reportRepository.findAllWithMemberAndStatus(status, pageable);
    }

    // 특정 신고서 상세 증거 대화 조회 및 인물 동적 라벨링
    public ReportAdmDetailDto getReportDetailForAdmin(UUID reportId) {
        // 1. 신고서 단건 조회 (없으면 404-1)
        Report report = reportRepository.findWithMemberByUuid(reportId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 신고서입니다."));

        // 2. 해당 신고서에 종속된 백업 대화 목록 시간순(ASC) 획득
        List<ReportedMessage> backupMessages = reportedMessageRepository.findByReportIdOrderBySentAtAsc(report.getId());

        // 3. 동적 가독성 라벨링 매핑 정보 셋업 - senderMemberId(ReportedMessage)가 FK 없는 UUID
        // 특수 필드라 여기서도 uuid로 비교해야 한다
        UUID reporterId = report.getReporter() != null ? report.getReporter().getUuid() : null;
        UUID reportedId = report.getReported() != null ? report.getReported().getUuid() : null;

        char participantSuffix = 'A';
        Map<UUID, String> participantMap = new HashMap<>();
        List<ReportAdmDetailDto.ReportedMessageAdmDto> messageDtos = new ArrayList<>();

        for (ReportedMessage msg : backupMessages) {
            String label;
            UUID senderId = msg.getSenderMemberId();

            if (reporterId != null && reporterId.equals(senderId)) {
                label = "신고자";
            } else if (reportedId != null && reportedId.equals(senderId)) {
                label = "피신고자";
            } else {
                // 처음 등장하는 제3의 참여자인 경우에만 직관적으로 맵에 넣고 알파벳 증가
                if (!participantMap.containsKey(senderId)) {
                    participantMap.put(senderId, "참여자 " + participantSuffix);
                    participantSuffix++; // 신규 참여자 최초 등록 시점에만 1씩 증가
                }
                label = participantMap.get(senderId);
            }


            // ReportedMessageAdmDto 객체 생성 및 적재
            messageDtos.add(new ReportAdmDetailDto.ReportedMessageAdmDto(
                    msg.getSenderNickname(),
                    label,
                    msg.getContent(),
                    msg.getSentAt(),
                    msg.isTarget()
            ));
        }

        return new ReportAdmDetailDto(report, messageDtos);
    }

    // 특정 신고서 처리 상태 수정 토글
    @Transactional
    public ReportStatusUpdateDto toggleReportStatus(UUID reportId) {
        Report report = reportRepository.findByUuid(reportId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 신고서입니다."));

        report.toggleStatus();

        return new ReportStatusUpdateDto(report.getUuid(), report.getStatus());
    }
}