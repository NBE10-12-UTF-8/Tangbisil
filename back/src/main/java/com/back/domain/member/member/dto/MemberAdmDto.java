package com.back.domain.member.member.dto;

import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;

import java.time.LocalDateTime;

public record MemberAdmDto(
        String memberId,
        String email,
        Industry industry,
        boolean isSuspended,
        LocalDateTime createdAt,
        String role
) {
    public MemberAdmDto(Member member) {
        this(
                member.getUuid().toString(),
                member.getEmail(),
                member.getIndustry(),
                member.isSuspended(),
                member.getCreatedAt(),
                member.getRole()
        );
    }
}