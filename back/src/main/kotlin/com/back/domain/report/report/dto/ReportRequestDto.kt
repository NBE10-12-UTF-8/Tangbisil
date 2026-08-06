package com.back.domain.report.report.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class ReportRequestDto(
    @field:NotNull val roomId: UUID?,
    @field:NotNull val reportedMessageId: UUID?,
    @field:NotBlank @field:Size(max = 500) val reason: String?
)
