package com.back.domain.member.member.controller

import com.back.domain.member.member.dto.MemberAdmDto
import com.back.domain.member.member.service.MemberService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/members")
@Tag(name = "ApiV1AdmMemberController", description = "관리자용 API 회원 컨트롤러")
@SecurityRequirement(name = "bearerAuth")
class ApiV1AdmMemberController(
    private val memberService: MemberService,
    private val rq: Rq
) {
    @GetMapping
    @Operation(summary = "회원 다건 조회")
    fun getItems(
        @RequestParam(required = false) isSuspended: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): RsData<Page<MemberAdmDto>> {
        val pageable: Pageable = PageRequest.of(page, size)
        val members = memberService.findAll(isSuspended, pageable).map { MemberAdmDto(it) }

        return RsData(
            "200-1",
            "회원 다건 조회 성공",
            members
        )
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "회원 단건 조회 (UUID 또는 이메일)")
    fun getItem(@PathVariable memberId: String): RsData<MemberAdmDto> {
        val member = memberService.findByIdentifier(memberId)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }

        return RsData(
            "200-1",
            "회원 단건 조회 성공",
            MemberAdmDto(member)
        )
    }

    @PatchMapping("/{memberId}/suspend")
    @Operation(summary = "회원 제재 상태 변경")
    fun toggleMemberSuspension(@PathVariable memberId: UUID): RsData<MemberAdmDto> {
        val adminActor = rq.actor

        val responseDto = memberService.toggleMemberSuspension(memberId, adminActor)

        return RsData(
            "200-1",
            "계정 정지 상태 토글 성공",
            responseDto
        )
    }
}
