package com.back.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MatchNotificationDto {
    private String type;
    private UUID roomId;
    private String message;
    private LocalDateTime createdAt;
}