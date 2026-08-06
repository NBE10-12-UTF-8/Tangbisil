package com.back.domain.report.report.dto

import com.back.domain.report.report.entity.ReportStatus
import java.util.UUID

data class ReportStatusUpdateDto(
    val reportId: UUID,
    val status: ReportStatus
)
