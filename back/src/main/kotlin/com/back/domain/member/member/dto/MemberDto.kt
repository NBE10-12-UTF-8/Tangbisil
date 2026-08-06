package com.back.domain.member.member.dto

import com.back.domain.member.member.entity.Industry
import com.back.domain.member.member.entity.Member

data class MemberDto(
    val id: String,
    val email: String?,
    val industry: Industry?
) {
    constructor(member: Member) : this(member.uuid.toString(), member.email, member.industry)
}
