package com.back.domain.report.report.repository;

import com.back.domain.member.member.entity.Member;
import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterAndReportedMessageId(Member reporter, UUID reportedMessageId);

    Optional<Report> findByUuid(UUID uuid);

    // 한 번의 쿼리로 reporter와 reported 회원 정보를 같이 긁어와 N+1 원천 방지
    @Query(
            value = "select r from Report r left join fetch r.reporter left join fetch r.reported",
            countQuery = "select count(r) from Report r"
    )
    Page<Report> findAllWithMember(Pageable pageable);

    // N+1 방지 Fetch Join + 특정 처리 상태 필터링 페이징 조회
    @Query(
            value = "select r from Report r " +
                    "left join fetch r.reporter " +
                    "left join fetch r.reported " +
                    "where r.status = :status",
            countQuery = "select count(r) from Report r where r.status = :status"
    )
    Page<Report> findAllWithMemberAndStatus(
            @Param("status") ReportStatus status,
            Pageable pageable
    );

    // 단건 상세 조회 시에도 reporter와 reported를 한 번에 Join하여 N+1 쿼리 차단
    @EntityGraph(attributePaths = {"reporter", "reported"})
    Optional<Report> findWithMemberByUuid(UUID uuid);
}