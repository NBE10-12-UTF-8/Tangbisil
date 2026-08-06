package com.back.domain.report.report.entity

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.member.member.entity.Member
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

@Entity
class Report(
    reporter: Member?,
    reported: Member?,
    room: ChatRoom,
    reportedMessageId: UUID,
    reason: String?
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var reporter: Member? = reporter // 신고자
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var reported: Member? = reported // 피신고자
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    var room: ChatRoom = room // 발생한 채팅방
        protected set

    // 신고 대상 원본 ChatMessage의 ID.
    // 원본 메시지는 24시간 후 Hard Delete 되므로 참조 무결성 예외 방지를 위해 외래키(FK) 없이 UUID만 보관함.
    var reportedMessageId: UUID = reportedMessageId
        protected set

    @Column(columnDefinition = "TEXT")
    var reason: String? = reason // 신고 사유
        protected set

    @Enumerated(EnumType.STRING)
    var status: ReportStatus = ReportStatus.PENDING
        protected set

    // PENDING | PROCESSED 처리 상태 토글
    fun toggleStatus() {
        status = if (status == ReportStatus.PENDING) ReportStatus.PROCESSED else ReportStatus.PENDING
    }
}
