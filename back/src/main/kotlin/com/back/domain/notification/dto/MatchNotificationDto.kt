package com.back.domain.notification.dto

import java.time.LocalDateTime
import java.util.UUID

data class MatchNotificationDto(
    val type: String,
    val roomId: UUID,
    val message: String,
    val createdAt: LocalDateTime
)
