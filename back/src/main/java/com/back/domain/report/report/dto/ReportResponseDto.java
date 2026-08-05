package com.back.domain.report.report.dto;

import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReportResponseDto(
        UUID reportId,
        ReportStatus status,
        LocalDateTime createdAt
) {
    public ReportResponseDto(Report report) {
        this(
                report.getUuid(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}