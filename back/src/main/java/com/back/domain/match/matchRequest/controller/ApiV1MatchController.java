package com.back.domain.match.matchRequest.controller;

import com.back.domain.match.matchRequest.dto.MatchRequestDto;
import com.back.domain.match.matchRequest.dto.MatchResponseDto;
import com.back.domain.match.matchRequest.dto.SituationStatisticsDto;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import com.back.global.rq.Rq;
import com.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Tag(name = "ApiV1MatchController", description = "API 매칭 컨트롤러")
public class ApiV1MatchController {
    private final MatchRequestService matchRequestService;
    private final Rq rq;

    private final MemberService memberService;

    @PostMapping
    @Operation(summary = "매칭 요청 생성")
    public RsData<MatchResponseDto> create(
            @RequestBody @Valid MatchRequestDto dto
    ) {
        Member actor = memberService.findById(rq.getActor().getId())
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));
        MatchRequest matchRequest = matchRequestService.create(actor, dto.situation());

        return new RsData<>(
                "201-1",
                "매칭 요청 생성 성공",
                MatchResponseDto.ofCreated(matchRequest)
        );

    }

    @GetMapping("/{matchRequestId}")
    @Operation(summary = "매칭 상태 조회")
    public RsData<MatchResponseDto> getMatchRequest(
            @PathVariable UUID matchRequestId
    ) {
        MatchRequest matchRequest = matchRequestService.findById(matchRequestId);

        if (matchRequest.getStatus() == MatchStatus.MATCHED) {
            return new RsData<>(
                    "200-1",
                    "매칭 성공",
                    MatchResponseDto.ofMatched(matchRequest.getRoom().getUuid())
            );
        }

        return new RsData<>(
                "200-2",
                "매칭 대기 중",
                MatchResponseDto.ofPending()
        );
    }

    @DeleteMapping("/{matchRequestId}")
    @Operation(summary = "매칭 취소")
    public RsData<Void> cancel(@PathVariable UUID matchRequestId) {
        Member actor = rq.getActor();
        MatchRequest matchRequest = matchRequestService.findById(matchRequestId);

        matchRequestService.cancel(matchRequest, actor);

        return new RsData<>(
                "200-1",
                "매칭 요청이 취소되었습니다."
        );
    }

    public record HomeStatsRes(
            @Schema(description = "현재 대화 중인 전체 인원 수 (상황별 인원의 합)")
            long totalActiveUsers,
            @Schema(description = "상황별 현재 대화 중인 인원 통계 목록")
            List<SituationStatisticsDto> situationStats
    ) {}

    @GetMapping("/stats/home")
    @Operation(summary = "홈 화면 실시간 통계 조회")
    public RsData<HomeStatsRes> getHomeStats() {
        List<SituationStatisticsDto> situationStats = matchRequestService.getSituationStatistics();
        long totalActiveUsers = situationStats.stream()
                .mapToLong(SituationStatisticsDto::count)
                .sum();

        return new RsData<>(
                "200-1",
                "홈 화면 통계 조회 성공",
                new HomeStatsRes(totalActiveUsers, situationStats)
        );
    }

}

