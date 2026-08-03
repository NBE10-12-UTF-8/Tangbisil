package com.back.domain.dashboard.dashboard.service;

import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.dashboard.dashboard.dto.*;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private final MemberRepository memberRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final ChatRoomRepository chatRoomRepository;

    private static final int RECENT_MATCH_LOG_SIZE = 10;
    // room 기준 중복 제거를 하고도 목표 개수(10)를 채울 수 있게 넉넉히 가져오는 배치 크기
    private static final int RECENT_MATCH_FETCH_BATCH_SIZE = 20;


    public DashboardResponseDto getDashboard() {
        long totalMembers = memberRepository.count();
        long todayMatches = matchRequestRepository.countTodayMatches(
                LocalDateTime.now().toLocalDate().atStartOfDay(),
                LocalDateTime.now(),
                MatchStatus.MATCHED
        );
        long activeChatRooms = chatRoomRepository.countByStatus(ChatRoomStatus.ACTIVE);
        long pendingMatches = matchRequestRepository.countByStatus(MatchStatus.PENDING);

        List<IndustryStatisticsDto> industryStatistics =
                matchRequestRepository.countMatchedRoomsByIndustry(MatchStatus.MATCHED);

        List<RecentMatchLogDto> recentMatchLogs = getRecentMatchLogs();

        return new DashboardResponseDto(
                new MatchStatisticsDto(totalMembers, todayMatches, activeChatRooms, pendingMatches),
                industryStatistics,
                recentMatchLogs
        );
    }
    // 매칭 성사 시 참여자 양쪽에 MatchRequest가 하나씩 생겨서 같은 room이 두 번 잡힌다.
    // room 기준으로 먼저 나온 것만 남기고, 회원 식별 정보 없이 날짜/산업군/상황만 노출한다.
    private List<RecentMatchLogDto> getRecentMatchLogs() {
        Set<UUID> seenRoomIds = new HashSet<>();
        return matchRequestRepository
                .findRecentByStatus(MatchStatus.MATCHED, PageRequest.of(0, RECENT_MATCH_FETCH_BATCH_SIZE))
                .stream()
                .filter(r -> seenRoomIds.add(r.getRoom().getId()))
                .limit(RECENT_MATCH_LOG_SIZE)
                .map(r -> new RecentMatchLogDto(r.getModifiedAt(), r.getIndustry(), r.getSituation()))
                .toList();
    }

    // 기간별 산업군 가입 통계 - startDate 00:00부터 endDate 다음날 00:00 직전까지
    // (endDate 하루 전체를 포함하도록 배타적 상한을 하루 뒤로 잡음)
    public IndustrySignupStatisticsResponseDto getIndustrySignupStatistics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ServiceException("400-1", "시작일은 종료일보다 늦을 수 없습니다.");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<IndustryStatisticsDto> industryStatistics =
                memberRepository.countByIndustryAndCreatedAtBetween(start, end);

        return new IndustrySignupStatisticsResponseDto(startDate, endDate, industryStatistics);
    }
}