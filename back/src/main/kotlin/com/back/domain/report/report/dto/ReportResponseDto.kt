package com.back.domain.report.report.dto

import com.back.domain.report.report.entity.Report
import com.back.domain.report.report.entity.ReportStatus
import java.time.LocalDateTime
import java.util.UUID

data class ReportResponseDto(
    val reportId: UUID,
    val status: ReportStatus,
    val createdAt: LocalDateTime
) {
    constructor(report: Report) : this(report.uuid, report.status, report.createdAt!!)
}
