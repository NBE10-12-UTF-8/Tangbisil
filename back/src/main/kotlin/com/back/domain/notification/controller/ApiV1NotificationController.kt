package com.back.domain.notification.controller

import com.back.domain.notification.dto.MatchNotificationDto
import com.back.domain.notification.service.MatchNotificationService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "ApiV1NotificationController", description = "API 알림 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1NotificationController(
    private val matchNotificationService: MatchNotificationService,
    private val rq: Rq
) {
    @GetMapping
    @Operation(summary = "알림 조회(폴링)")
    fun getNotifications(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) after: LocalDateTime?
    ): RsData<List<MatchNotificationDto>> {
        val actor = rq.actor ?: throw ServiceException("401-1", "인증이 필요합니다.")

        val notifications = matchNotificationService.getNotifications(actor.id, after)
        if (notifications.isEmpty()) {
            return RsData("200-2", "신규 알림 없음", null)
        }
        return RsData("200-1", "알림 목록 조회 성공", notifications)
    }
}
