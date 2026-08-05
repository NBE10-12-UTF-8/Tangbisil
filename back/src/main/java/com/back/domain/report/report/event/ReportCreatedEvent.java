package com.back.domain.report.report.event;

import java.util.UUID;

public record ReportCreatedEvent(
        Long reportId,
        Long roomId,
        UUID targetMessageId
) {
}