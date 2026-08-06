package com.back.domain.report.report.event

import java.util.UUID

data class ReportCreatedEvent(
    val reportId: Long,
    val roomId: Long,
    val targetMessageId: UUID
)
