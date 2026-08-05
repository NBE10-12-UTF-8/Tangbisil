package com.back.domain.match.matchRequest.controller

import com.back.domain.match.matchRequest.dto.MatchRequestDto
import com.back.domain.match.matchRequest.dto.MatchResponseDto
import com.back.domain.match.matchRequest.dto.SituationStatisticsDto
import com.back.domain.match.matchRequest.entity.MatchStatus
import com.back.domain.match.matchRequest.service.MatchRequestService
import com.back.domain.member.member.service.MemberService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "ApiV1MatchController", description = "API 매칭 컨트롤러")
class ApiV1MatchController(
    private val matchRequestService: MatchRequestService,
    private val rq: Rq,
    private val memberService: MemberService
) {

    @PostMapping
    @Operation(summary = "매칭 요청 생성")
    fun create(@RequestBody @Valid dto: MatchRequestDto): RsData<MatchResponseDto> {
        val actor = memberService.findById(rq.actor.id)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }
        val matchRequest = matchRequestService.create(actor, dto.situation!!)

        return RsData(
            "201-1",
            "매칭 요청 생성 성공",
            MatchResponseDto.ofCreated(matchRequest)
        )
    }

    @GetMapping("/{matchRequestId}")
    @Operation(summary = "매칭 상태 조회")
    fun getMatchRequest(@PathVariable matchRequestId: UUID): RsData<MatchResponseDto> {
        val matchRequest = matchRequestService.findById(matchRequestId)

        if (matchRequest.status == MatchStatus.MATCHED) {
            return RsData(
                "200-1",
                "매칭 성공",
                MatchResponseDto.ofMatched(matchRequest.room!!.uuid)
            )
        }

        return RsData(
            "200-2",
            "매칭 대기 중",
            MatchResponseDto.ofPending()
        )
    }

    @DeleteMapping("/{matchRequestId}")
    @Operation(summary = "매칭 취소")
    fun cancel(@PathVariable matchRequestId: UUID): RsData<Void> {
        val actor = rq.actor
        val matchRequest = matchRequestService.findById(matchRequestId)

        matchRequestService.cancel(matchRequest, actor)

        return RsData("200-1", "매칭 요청이 취소되었습니다.")
    }

    data class HomeStatsRes(
        @param:Schema(description = "현재 대화 중인 전체 인원 수 (상황별 인원의 합)")
        val totalActiveUsers: Long,
        @param:Schema(description = "상황별 현재 대화 중인 인원 통계 목록")
        val situationStats: List<SituationStatisticsDto>
    )

    @GetMapping("/stats/home")
    @Operation(summary = "홈 화면 실시간 통계 조회")
    fun getHomeStats(): RsData<HomeStatsRes> {
        val situationStats = matchRequestService.getSituationStatistics()
        val totalActiveUsers = situationStats.sumOf { it.count }

        return RsData(
            "200-1",
            "홈 화면 통계 조회 성공",
            HomeStatsRes(totalActiveUsers, situationStats)
        )
    }
}
