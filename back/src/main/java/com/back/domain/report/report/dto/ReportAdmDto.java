package com.back.domain.report.report.dto;

import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReportAdmDto(
        UUID reportId,
        String reporterEmail,
        String reportedEmail,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt
) {
    public ReportAdmDto(Report report) {
        this(
                report.getUuid(),
                report.getReporter() != null ? report.getReporter().getEmail() : "탈퇴한 사용자",
                report.getReported() != null ? report.getReported().getEmail() : "탈퇴한 사용자",
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}