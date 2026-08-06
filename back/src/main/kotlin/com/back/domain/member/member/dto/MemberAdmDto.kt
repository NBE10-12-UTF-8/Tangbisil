package com.back.domain.member.member.dto

import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.entity.Member
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class MemberAdmDto(
    val memberId: String,
    val email: String?,
    val industry: Industry?,
    @get:JsonProperty("isSuspended") val isSuspended: Boolean,
    val createdAt: LocalDateTime,
    val role: String?
) {
    constructor(member: Member) : this(
        member.uuid.toString(),
        member.email,
        member.industry,
        member.isSuspended,
        member.createdAt,
        member.role
    )
}
