package com.back.domain.dashboard.dashboard.service

import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import com.back.domain.dashboard.dashboard.dto.DashboardResponseDto
import com.back.domain.dashboard.dashboard.dto.IndustrySignupStatisticsResponseDto
import com.back.domain.dashboard.dashboard.dto.MatchStatisticsDto
import com.back.domain.dashboard.dashboard.dto.RecentMatchLogDto
import com.back.domain.match.matchRequest.entity.MatchStatus
import com.back.domain.match.matchRequest.repository.MatchRequestRepository
import com.back.domain.member.member.repository.MemberRepository
import com.back.global.exception.ServiceException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val memberRepository: MemberRepository,
    private val matchRequestRepository: MatchRequestRepository,
    private val chatRoomRepository: ChatRoomRepository
) {
    companion object {
        private const val RECENT_MATCH_LOG_SIZE = 10

        // room 기준 중복 제거를 하고도 목표 개수(10)를 채울 수 있게 넉넉히 가져오는 배치 크기
        private const val RECENT_MATCH_FETCH_BATCH_SIZE = 20
    }

    fun getDashboard(): DashboardResponseDto {
        val totalMembers = memberRepository.count()
        val todayMatches = matchRequestRepository.countTodayMatches(
            LocalDateTime.now().toLocalDate().atStartOfDay(),
            LocalDateTime.now(),
            MatchStatus.MATCHED
        )
        val activeChatRooms = chatRoomRepository.countByStatus(ChatRoomStatus.ACTIVE)
        val pendingMatches = matchRequestRepository.countByStatus(MatchStatus.PENDING)

        val industryStatistics = matchRequestRepository.countMatchedRoomsByIndustry(MatchStatus.MATCHED)

        val recentMatchLogs = getRecentMatchLogs()

        return DashboardResponseDto(
            MatchStatisticsDto(totalMembers, todayMatches, activeChatRooms, pendingMatches),
            industryStatistics,
            recentMatchLogs
        )
    }

    // 매칭 성사 시 참여자 양쪽에 MatchRequest가 하나씩 생겨서 같은 room이 두 번 잡힌다.
    // room 기준으로 먼저 나온 것만 남기고, 회원 식별 정보 없이 날짜/산업군/상황만 노출한다.
    private fun getRecentMatchLogs(): List<RecentMatchLogDto> {
        val seenRoomIds = HashSet<Long>()
        return matchRequestRepository
            .findRecentByStatus(MatchStatus.MATCHED, PageRequest.of(0, RECENT_MATCH_FETCH_BATCH_SIZE))
            .filter { seenRoomIds.add(it.room!!.id!!) }
            .take(RECENT_MATCH_LOG_SIZE)
            .map { RecentMatchLogDto(it.modifiedAt!!, it.industry, it.situation) }
    }

    // 기간별 산업군 가입 통계 - startDate 00:00부터 endDate 다음날 00:00 직전까지
    // (endDate 하루 전체를 포함하도록 배타적 상한을 하루 뒤로 잡음)
    fun getIndustrySignupStatistics(startDate: LocalDate?, endDate: LocalDate?): IndustrySignupStatisticsResponseDto {
        if (startDate == null || endDate == null) {
            throw ServiceException("400-1", "시작일과 종료일은 필수 입력값입니다.")
        }
        if (startDate.isAfter(endDate)) {
            throw ServiceException("400-1", "시작일은 종료일보다 늦을 수 없습니다.")
        }

        val start = startDate.atStartOfDay()
        val end = endDate.plusDays(1).atStartOfDay()

        val industryStatistics =
            memberRepository.countByIndustryAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)
        return IndustrySignupStatisticsResponseDto(startDate, endDate, industryStatistics)
    }
}
