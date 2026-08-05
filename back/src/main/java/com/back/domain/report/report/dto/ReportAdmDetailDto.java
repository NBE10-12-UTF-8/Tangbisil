package com.back.domain.report.report.dto;

import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReportAdmDetailDto(
        UUID reportId,
        String reporterEmail,
        String reportedEmail,
        ReportStatus status,
        List<ReportedMessageAdmDto> reportedMessages
) {
    public ReportAdmDetailDto(Report report, List<ReportedMessageAdmDto> reportedMessages) {
        this(
                report.getUuid(),
                report.getReporter() != null ? report.getReporter().getEmail() : "탈퇴한 사용자",
                report.getReported() != null ? report.getReported().getEmail() : "탈퇴한 사용자",
                report.getStatus(),
                reportedMessages
        );
    }

    public record ReportedMessageAdmDto(
            String senderNickname,
            String senderLabel,
            String content,
            LocalDateTime sentAt,
            boolean isTarget
    ) {
    }
}