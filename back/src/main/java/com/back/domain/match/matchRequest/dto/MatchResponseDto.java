package com.back.domain.match.matchRequest.dto;

import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchResponseDto(
        String matchRequestId,
        MatchStatus status,
        LocalDateTime requestedAt,
        String chatRoomId
) {
    public static MatchResponseDto ofCreated(MatchRequest matchRequest) {
        return new MatchResponseDto(
                matchRequest.getUuid().toString(),
                matchRequest.getStatus(),
                matchRequest.getRequestedAt(),
                null
        );
    }

    public static MatchResponseDto ofMatched(UUID chatRoomId) {
        return new MatchResponseDto(null, MatchStatus.MATCHED, null, chatRoomId.toString());
    }

    public static MatchResponseDto ofPending() {

        return new MatchResponseDto(
                null,
                MatchStatus.PENDING,
                null,
                null);
    }
}
