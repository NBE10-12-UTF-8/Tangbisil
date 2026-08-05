package com.back.domain.match.matchRequest.repository;

import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto;
import com.back.domain.match.matchRequest.dto.SituationStatisticsDto;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    boolean existsByMemberAndStatus(Member member, MatchStatus status);

    Optional<MatchRequest> findByUuid(UUID uuid);

    long countByStatus(MatchStatus status);

    @Query("""
           SELECT r FROM MatchRequest r
           JOIN FETCH r.member
           WHERE r.industry = :industry AND r.situation = :situation AND r.status = :status
           ORDER BY r.requestedAt ASC
           """)
    List<MatchRequest> findPendingByIndustryAndSituation(
            @Param("industry") Industry industry,
            @Param("situation") Situation situation,
            @Param("status") MatchStatus status);

    // 같은 업종 + 비슷한 상황 여러개
    @Query("""
           SELECT r FROM MatchRequest r
           JOIN FETCH r.member
           WHERE r.industry = :industry AND r.situation IN :situations AND r.status = :status
           ORDER BY r.requestedAt ASC
           """)
    List<MatchRequest> findPendingByIndustryAndSituations(
            @Param("industry") Industry industry,
            @Param("situations") Collection<Situation> situations,
            @Param("status") MatchStatus status);

    @Query("""
           SELECT r FROM MatchRequest r
           JOIN FETCH r.member
           WHERE r.industry = :industry AND r.status = :status
           ORDER BY r.requestedAt ASC
           """)
    List<MatchRequest> findPendingByIndustry(
            @Param("industry") Industry industry,
            @Param("status") MatchStatus status);
    @Query("""
         SELECT new com.back.domain.match.matchRequest.dto.SituationStatisticsDto(r.situation, COUNT(r))
         FROM MatchRequest r
         WHERE r.status = :status AND r.room.status = :roomStatus
         GROUP BY r.situation
         ORDER BY COUNT(r) DESC, r.situation ASC
         """)
    List<SituationStatisticsDto> countActiveBySituation(
            @Param("status") MatchStatus status,
            @Param("roomStatus") ChatRoomStatus roomStatus);
    // retryPendingMatches의 재시도 대상 조회 - member까지 즉시 로딩해서
    // REQUIRES_NEW로 트랜잭션이 분리되는 재시도 처리 도중 지연 로딩 프록시가
    // 세션 없이 초기화되는 LazyInitializationException을 방지한다.
    @Query("""
           SELECT r FROM MatchRequest r
           JOIN FETCH r.member
           WHERE r.status = :status
           ORDER BY r.requestedAt ASC
           """)
    List<MatchRequest> findAllByStatus(@Param("status") MatchStatus status);

    @Query("SELECT COUNT(DISTINCT r.room.id) FROM MatchRequest r WHERE r.status = :status AND r.createdAt BETWEEN :start AND :end")
    long countTodayMatches(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") MatchStatus status
    );

    @Query("SELECT r FROM MatchRequest r WHERE r.status = :status AND r.requestedAt < :expiredBefore")
    List<MatchRequest> findExpiredPending(
            @Param("status") MatchStatus status,
            @Param("expiredBefore") LocalDateTime expiredBefore);

    @Query("""
       SELECT r FROM MatchRequest r
       JOIN FETCH r.room
       WHERE r.member = :member AND r.room.status = :status
       ORDER BY r.room.createdAt DESC
       """)
    List<MatchRequest> findByMemberAndRoomStatus(
            @Param("member") Member member,
            @Param("status") ChatRoomStatus status);

    @Query("SELECT mr FROM MatchRequest mr JOIN FETCH mr.room WHERE mr.status = :status ORDER BY mr.modifiedAt DESC")
    List<MatchRequest> findRecentByStatus(@Param("status") MatchStatus status, Pageable pageable);

    @Query("""
           SELECT r FROM MatchRequest r
           JOIN FETCH r.member
           WHERE r.id = :id
           """)
    Optional<MatchRequest> findByIdWithMember(@Param("id") Long id);

    @Query("""
           SELECT r FROM MatchRequest r
           JOIN FETCH r.member
           WHERE r.uuid = :uuid
           """)
    Optional<MatchRequest> findByUuidWithMember(@Param("uuid") UUID uuid);

    // 매칭 후보 UUID 여러 개를 한 번에 조회 - 후보 하나당 쿼리 하나씩 날리던 N+1을 방지한다.
    List<MatchRequest> findAllByUuidIn(Collection<UUID> uuids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MatchRequest r WHERE r.member = :member")
    void deleteByMember(@Param("member") Member member);

    // 취소 시 "PENDING인지 확인 후 삭제"를 앱 코드에서 SELECT-then-DELETE로 하면, 그 사이에
    // 매칭 배치(processMatch)가 이 요청을 PENDING으로 읽어 확정시켜버릴 수 있다(취소 체크가
    // 이미 지난 스냅샷 기준이라 못 걸러냄). status 조건을 DELETE 문 자체에 넣어 DB가 최신
    // 커밋 상태 기준으로 원자적으로 처리하게 한다 - 매칭이 먼저 확정됐으면 0건 삭제되어
    // cancel()이 정상적으로 실패를 감지할 수 있다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MatchRequest r WHERE r.id = :id AND r.status = :status")
    int deleteByIdAndStatus(@Param("id") Long id, @Param("status") MatchStatus status);

    @Query("""
       SELECT new com.back.domain.dashboard.dashboard.dto.IndustryStatisticsDto(r.industry, COUNT(DISTINCT r.room.id))
       FROM MatchRequest r
       WHERE r.status = :status
       GROUP BY r.industry
       """)
    List<IndustryStatisticsDto> countMatchedRoomsByIndustry(@Param("status") MatchStatus status);

    // 매칭 상대의 MatchRequest 조회 - 같은 방(room)에서 나를 제외한 상대방의
    // situation을 노출하기 위한 용도 (봇 상대여도 matchWithBot에서 MatchRequest를
    // 만들어두므로 항상 존재함)
    @Query("""
       SELECT r FROM MatchRequest r
       WHERE r.room.id = :roomId AND r.member.id <> :memberId
       ORDER BY r.createdAt DESC
       """)
    List<MatchRequest> findByRoomIdAndMemberIdNot(
            @Param("roomId") Long roomId,
            @Param("memberId") Long memberId,
            Pageable pageable);
}