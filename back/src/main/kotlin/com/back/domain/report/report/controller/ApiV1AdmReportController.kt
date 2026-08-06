package com.back.domain.report.report.controller

import com.back.domain.report.report.dto.ReportAdmDetailDto
import com.back.domain.report.report.dto.ReportAdmDto
import com.back.domain.report.report.dto.ReportStatusUpdateDto
import com.back.domain.report.report.entity.ReportStatus
import com.back.domain.report.report.service.ReportService
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "ApiV1AdmReportController", description = "관리자용 API 신고 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1AdmReportController(
    private val reportService: ReportService
) {
    @GetMapping
    @Operation(summary = "신고 목록 조회")
    fun getList(
        @RequestParam(required = false) status: ReportStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): RsData<Page<ReportAdmDto>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())

        val reports = reportService.findAllWithMember(status, pageable)
        val reportDtos = reports.map { ReportAdmDto(it) }
        return RsData(
            "200-1",
            "신고 목록 조회 성공",
            reportDtos
        )
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "신고 상세 조회")
    fun getItem(@PathVariable reportId: UUID): RsData<ReportAdmDetailDto> {
        val reportDetail = reportService.getReportDetailForAdmin(reportId)

        return RsData(
            "200-1",
            "신고 상세 조회 성공",
            reportDetail
        )
    }

    @PatchMapping("/{reportId}/status")
    @Operation(summary = "신고서 처리 상태 수정")
    fun toggleStatus(@PathVariable reportId: UUID): RsData<ReportStatusUpdateDto> {
        val statusUpdate = reportService.toggleReportStatus(reportId)

        return RsData(
            "200-1",
            "신고서 처리 상태 수정 성공",
            statusUpdate
        )
    }
}
