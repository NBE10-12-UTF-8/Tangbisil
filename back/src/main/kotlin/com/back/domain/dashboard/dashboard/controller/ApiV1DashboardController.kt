package com.back.domain.dashboard.dashboard.controller

import com.back.domain.dashboard.dashboard.dto.DashboardResponseDto
import com.back.domain.dashboard.dashboard.dto.IndustrySignupStatisticsResponseDto
import com.back.domain.dashboard.dashboard.service.DashboardService
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "ApiV1DashboardController", description = "관리자 대시보드 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1DashboardController(
    private val dashboardService: DashboardService
) {
    @GetMapping
    @Operation(summary = "대시보드 통계 조회")
    fun getDashboard(): RsData<DashboardResponseDto> {
        return RsData(
            "200-1",
            "대시보드 통계 조회 성공",
            dashboardService.getDashboard()
        )
    }

    @GetMapping("/industry-signups")
    @Operation(summary = "기간별 산업군 가입 통계 조회")
    fun getIndustrySignupStatistics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): RsData<IndustrySignupStatisticsResponseDto> {
        return RsData(
            "200-1",
            "기간별 산업군 가입 통계 조회 성공",
            dashboardService.getIndustrySignupStatistics(startDate, endDate)
        )
    }
}
