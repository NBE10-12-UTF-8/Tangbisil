package com.back.domain.report.report.dto

import com.back.domain.report.report.entity.Report
import com.back.domain.report.report.entity.ReportStatus
import java.time.LocalDateTime
import java.util.UUID

data class ReportAdmDto(
    val reportId: UUID,
    val reporterEmail: String,
    val reportedEmail: String,
    val reason: String?,
    val status: ReportStatus,
    val createdAt: LocalDateTime
) {
    constructor(report: Report) : this(
        report.uuid,
        report.reporter?.email ?: "탈퇴한 사용자",
        report.reported?.email ?: "탈퇴한 사용자",
        report.reason,
        report.status,
        report.createdAt
    )
}
