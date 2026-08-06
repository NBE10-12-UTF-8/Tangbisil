package com.back.domain.report.report.repository

import com.back.domain.report.report.entity.ReportedMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReportedMessageRepository : JpaRepository<ReportedMessage, Long> {
    fun findByReportIdOrderBySentAtAsc(reportId: Long): List<ReportedMessage>

    fun findByUuid(uuid: UUID): ReportedMessage?
}
