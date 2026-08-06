package com.back.domain.report.report.dto

import com.back.domain.report.report.entity.Report
import com.back.domain.report.report.entity.ReportStatus
import java.time.LocalDateTime
import java.util.UUID

data class ReportAdmDetailDto(
    val reportId: UUID,
    val reporterEmail: String,
    val reportedEmail: String,
    val status: ReportStatus,
    val reportedMessages: List<ReportedMessageAdmDto>
) {
    constructor(report: Report, reportedMessages: List<ReportedMessageAdmDto>) : this(
        report.uuid,
        report.reporter?.email ?: "탈퇴한 사용자",
        report.reported?.email ?: "탈퇴한 사용자",
        report.status,
        reportedMessages
    )

    data class ReportedMessageAdmDto(
        val senderNickname: String?,
        val senderLabel: String,
        val content: String?,
        val sentAt: LocalDateTime,
        val isTarget: Boolean
    )
}
