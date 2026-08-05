package com.back.domain.report.report.repository;

import com.back.domain.report.report.entity.ReportedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportedMessageRepository extends JpaRepository<ReportedMessage, Long> {
    List<ReportedMessage> findByReportIdOrderBySentAtAsc(Long reportId);

    Optional<ReportedMessage> findByUuid(UUID uuid);
}