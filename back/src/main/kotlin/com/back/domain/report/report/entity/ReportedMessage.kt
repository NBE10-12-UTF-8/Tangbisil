package com.back.domain.report.report.entity

import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime
import java.util.UUID

@Entity
class ReportedMessage(
    report: Report,
    senderMemberId: UUID,
    senderNickname: String?,
    content: String?,
    sentAt: LocalDateTime,
    isTarget: Boolean
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    var report: Report = report
        protected set

    var senderMemberId: UUID = senderMemberId // 메시지 작성자 실제 회원 UUID
        protected set

    var senderNickname: String? = senderNickname // 메시지 작성자 닉네임
        protected set

    @Column(columnDefinition = "TEXT")
    var content: String? = content // 대화 내용 복사
        protected set

    var sentAt: LocalDateTime = sentAt // 원본 메시지 발송 시간 복사
        protected set

    var isTarget: Boolean = isTarget // 신고 유발 타겟 메시지 여부
        protected set
}
