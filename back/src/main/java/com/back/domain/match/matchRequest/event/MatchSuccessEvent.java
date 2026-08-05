package com.back.domain.match.matchRequest.event;

import java.util.UUID;

public record MatchSuccessEvent(
    UUID roomId,
    Long requesterId,
    Long opponentId
) {}