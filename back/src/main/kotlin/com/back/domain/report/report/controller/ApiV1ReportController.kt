package com.back.domain.report.report.controller

import com.back.domain.report.report.dto.ReportRequestDto
import com.back.domain.report.report.dto.ReportResponseDto
import com.back.domain.report.report.service.ReportService
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "ApiV1ReportController", description = "API 신고 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1ReportController(
    private val reportService: ReportService,
    private val rq: Rq
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "신고 접수")
    fun createReport(@RequestBody @Valid requestDto: ReportRequestDto): RsData<ReportResponseDto> {
        val actor = rq.actor

        val report = reportService.createReport(
            actor,
            requestDto.roomId!!,
            requestDto.reportedMessageId!!,
            requestDto.reason
        )

        return RsData(
            "201-1",
            "신고 생성 성공",
            ReportResponseDto(report)
        )
    }
}
